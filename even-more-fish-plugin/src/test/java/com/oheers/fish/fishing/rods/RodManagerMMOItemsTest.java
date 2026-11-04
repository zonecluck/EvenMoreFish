package com.oheers.fish.fishing.rods;

import com.oheers.fish.Checks;
import com.oheers.fish.api.baits.AbstractBaitManager;
import com.oheers.fish.config.MainConfig;
import com.oheers.fish.utils.nbt.NbtKeys;
import com.oheers.fish.utils.nbt.NbtUtils;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RodManagerMMOItemsTest {
    private static RodManager manager;

    @BeforeAll
    static void initializeManagerWithoutConfigurationMigration() {
        try (var baitDependency = mockStatic(AbstractBaitManager.class);
             var conversions = mockConstruction(RodConversion.class)) {
            manager = RodManager.getInstance();
        }
    }

    @BeforeEach
    void clearCatalog() {
        manager.getItemMap().clear();
    }

    @Test
    void knownMmoRodsPassTheCustomRodGateAndUseTheirConfiguredTierProfile() {
        Map<String, String> profiles = Map.of(
                "FISHING_ROD_1", "default", "FISHING_ROD_2", "linh_moc",
                "FISHING_ROD_3", "dia_mach", "FISHING_ROD_4", "nhu_y", "FISHING_ROD_5", "tan_the");
        MainConfig config = mock(MainConfig.class);
        when(config.requireCustomRod()).thenReturn(true);
        try (var nbt = mockStatic(NbtUtils.class); var main = mockStatic(MainConfig.class)) {
            main.when(MainConfig::getInstance).thenReturn(config);
            profiles.forEach((mmoId, profileId) -> {
                CustomRod profile = mock(CustomRod.class);
                manager.getItemMap().put(profileId, profile);
                ItemStack item = mmoItem(mmoId, Material.FISHING_ROD);
                assertSame(profile, manager.getRod(item), mmoId);
                assertTrue(Checks.canUseRod(item), mmoId + " must pass require-custom-rod");
                verify(item, never()).setItemMeta(any());
            });
        }
    }

    @Test
    void vanillaAndUnknownMmoRodsRemainRejected() {
        manager.getItemMap().put("default", mock(CustomRod.class));
        try (var nbt = mockStatic(NbtUtils.class)) {
            assertNull(manager.getRod(mmoItem("UNKNOWN_ROD", Material.FISHING_ROD)));
            assertNull(manager.getRod(mmoItem("ROD_50", Material.FISHING_ROD)));
            assertNull(manager.getRod(mmoItem("FISHING_ROD_6", Material.FISHING_ROD)));
            assertNull(manager.getRod(mmoItem("", Material.FISHING_ROD)));
            assertNull(manager.getRod((ItemStack) null));
        }
    }

    @Test
    void knownMmoIdOnANonRodDoesNotQualify() {
        manager.getItemMap().put("tan_the", mock(CustomRod.class));
        try (var nbt = mockStatic(NbtUtils.class)) {
            assertNull(manager.getRod(mmoItem("FISHING_ROD_5", Material.STICK)));
        }
    }

    @Test
    void missingTierProfileFailsClosedInsteadOfFallingBackToAnotherTier() {
        manager.getItemMap().put("default", mock(CustomRod.class));
        try (var nbt = mockStatic(NbtUtils.class)) {
            assertNull(manager.getRod(mmoItem("FISHING_ROD_5", Material.FISHING_ROD)));
        }
    }

    @Test
    void nativeRodIdentityRemainsAuthoritativeOverMmoIdentity() {
        CustomRod nativeRod = mock(CustomRod.class);
        CustomRod mmoProfile = mock(CustomRod.class);
        manager.getItemMap().put("native", nativeRod);
        manager.getItemMap().put("tan_the", mmoProfile);
        ItemStack item = mmoItem("FISHING_ROD_5", Material.FISHING_ROD);
        try (var nbt = mockStatic(NbtUtils.class)) {
            nbt.when(() -> NbtUtils.getString(item, NbtKeys.EMF_ROD_ID)).thenReturn("native");
            assertSame(nativeRod, manager.getRod(item));
        }
    }

    @Test
    void unknownNativeRodDoesNotFallBackToMmoProfile() {
        manager.getItemMap().put("tan_the", mock(CustomRod.class));
        ItemStack item = mmoItem("FISHING_ROD_5", Material.FISHING_ROD);
        try (var nbt = mockStatic(NbtUtils.class)) {
            nbt.when(() -> NbtUtils.getString(item, NbtKeys.EMF_ROD_ID)).thenReturn("deleted-profile");
            assertNull(manager.getRod(item));
        }
    }

    @Test
    void legacyRodStillUsesDefaultProfile() {
        CustomRod fallback = mock(CustomRod.class);
        manager.getItemMap().put("default", fallback);
        ItemStack item = mmoItem("FISHING_ROD_5", Material.FISHING_ROD);
        try (var nbt = mockStatic(NbtUtils.class)) {
            nbt.when(() -> NbtUtils.hasKey(item, NbtKeys.EMF_ROD_NBT)).thenReturn(true);
            assertSame(fallback, manager.getRod(item));
        }
    }

    private static ItemStack mmoItem(String id, Material material) {
        ItemStack item = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);
        when(item.getType()).thenReturn(material);
        when(item.hasItemMeta()).thenReturn(true);
        when(item.getItemMeta()).thenReturn(meta);
        when(meta.getPersistentDataContainer()).thenReturn(pdc);
        when(pdc.get(new NamespacedKey("mmoitems", "mmoitems_item_id"), PersistentDataType.STRING)).thenReturn(id);
        return item;
    }
}
