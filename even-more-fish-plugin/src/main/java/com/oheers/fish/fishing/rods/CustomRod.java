package com.oheers.fish.fishing.rods;

import com.oheers.fish.EvenMoreFish;
import com.oheers.fish.api.Logging;
import com.oheers.fish.api.config.ConfigBase;
import com.oheers.fish.api.fishing.rods.ICustomRod;
import com.oheers.fish.fishing.items.Fish;
import com.oheers.fish.fishing.items.FishManager;
import com.oheers.fish.fishing.items.Rarity;
import com.oheers.fish.items.ItemFactory;
import com.oheers.fish.recipe.EMFRecipe;
import com.oheers.fish.recipe.RecipeUtil;
import com.oheers.fish.utils.nbt.NbtKeys;
import de.tr7zw.changeme.nbtapi.NBT;
import de.tr7zw.changeme.nbtapi.iface.ReadWriteNBT;
import dev.dejvokep.boostedyaml.block.implementation.Section;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

public class CustomRod extends ConfigBase implements ICustomRod {

    private final @NotNull String id;
    private final List<Rarity> allowedRarities;
    private final List<Fish> allowedFish;
    private final EMFRecipe<?> recipe;
    private final ItemFactory factory;

    private final double luckModifier;
    private final double lureModifier;
    private final double doubleCatchChance;
    private final double expMultiplier;
    private final Map<String, Double> rarityModifiers;
    private final boolean allowBaits;
    private final boolean unbreakable;
    private final @Nullable String particleEffect;

    public CustomRod(@NotNull File file) throws InvalidConfigurationException {
        super(file, EvenMoreFish.getInstance(), false);
        this.id = validateId();
        this.allowedRarities = loadAllowedRarities();
        this.allowedFish = loadAllowedFish();

        this.luckModifier = getConfig().getDouble("stats.luck-modifier", getConfig().getDouble("luck-modifier", 0.0));
        this.lureModifier = getConfig().getDouble("stats.lure-modifier", getConfig().getDouble("lure-modifier", 0.0));
        this.doubleCatchChance = getConfig().getDouble("stats.double-catch-chance", getConfig().getDouble("double-catch-chance", 0.0));
        this.expMultiplier = getConfig().getDouble("stats.exp-multiplier", getConfig().getDouble("exp-multiplier", 1.0));
        this.allowBaits = getConfig().getBoolean("allow-baits", true);
        this.unbreakable = getConfig().getBoolean("unbreakable", false);
        this.particleEffect = getConfig().getString("particle-effect", null);
        this.rarityModifiers = loadRarityModifiers();

        this.factory = ItemFactory.itemFactory(getConfig());
        this.factory.setFinalChanges(item -> {
            if (this.unbreakable) {
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    meta.setUnbreakable(true);
                    item.setItemMeta(meta);
                }
            }
            NBT.modify(item, nbt -> {
                ReadWriteNBT emfCompound = nbt.getOrCreateCompound(NbtKeys.EMF_COMPOUND);
                emfCompound.setString(NbtKeys.EMF_ROD_ID, getId());
            });
        });
        this.recipe = loadRecipe();
    }

    private String validateId() throws InvalidConfigurationException {
        String id = getConfig().getString("id");
        if (id == null) {
            throw new InvalidConfigurationException("CustomRod " + getFileName() + " has no configured id.");
        }
        return id;
    }

    private Map<String, Double> loadRarityModifiers() {
        Map<String, Double> map = new HashMap<>();
        Section section = getConfig().getSection("rarity-modifiers");
        if (section == null) {
            section = getConfig().getSection("stats.rarity-modifiers");
        }
        if (section != null) {
            for (String key : section.getRoutesAsStrings(false)) {
                map.put(key, section.getDouble(key, 1.0));
            }
        }
        return map;
    }

    // Loading things

    /**
     * @return the contents of the "allowed-rarities" section, or an empty list if the section does not exist.
     */
    public List<Rarity> loadAllowedRarities() {
        if ("ALL".equalsIgnoreCase(getConfig().getString("allowed-rarities"))) {
            return FishManager.getInstance().getRarityMap().values().stream().toList();
        }
        return getConfig().getStringList("allowed-rarities").stream()
            .map(id -> FishManager.getInstance().getRarity(id))
            .filter(Objects::nonNull)
            .toList();
    }

    /**
     * @return the contents of the "allowed-fish" section, or an empty list if the section does not exist.
     */
    public List<Fish> loadAllowedFish() {
        Section section = getConfig().getSection("allowed-fish");
        if (section == null) {
            return List.of();
        }

        return section.getRoutesAsStrings(false).stream()
            .flatMap(rarityId -> {
                Rarity rarity = FishManager.getInstance().getRarity(rarityId);
                if (rarity == null) {
                    Logging.warn("Rarity '" + rarityId + "' not found for custom rod '" + getId() + "'.");
                    return Stream.empty();
                }
                if (!allowedRarities.contains(rarity)) {
                    Logging.warn("Rarity '" + rarityId + "' is not allowed for custom rod '" + getId() + "'.");
                    return Stream.empty();
                }
                return section.getStringList(rarityId)
                    .stream()
                    .map(rarity::getFish)
                    .filter(Objects::nonNull);
            })
            .toList();
    }

    private EMFRecipe<?> loadRecipe() {
        Section section = getConfig().getSection("recipe");
        if (section == null) {
            return null;
        }
        return RecipeUtil.getRecipe(
            section,
            getRecipeKey(),
            create()
        );
    }

    // Config getters

    @Override
    public @NotNull String getId() {
        return this.id;
    }

    private @NotNull NamespacedKey getRecipeKey() {
        return new NamespacedKey(EvenMoreFish.getInstance(), "customrod-" + getId());
    }

    @Override
    public boolean isDisabled() {
        return getConfig().getBoolean("disabled");
    }

    @Override
    public double getLuckModifier() {
        return this.luckModifier;
    }

    @Override
    public double getLureModifier() {
        return this.lureModifier;
    }

    @Override
    public double getDoubleCatchChance() {
        return this.doubleCatchChance;
    }

    @Override
    public double getExpMultiplier() {
        return this.expMultiplier;
    }

    @Override
    public @NotNull Map<String, Double> getRarityMultipliers() {
        return this.rarityModifiers;
    }

    @Override
    public boolean isBaitAllowed() {
        return this.allowBaits;
    }

    @Override
    public boolean isUnbreakable() {
        return this.unbreakable;
    }

    @Override
    public @Nullable String getParticleEffect() {
        return this.particleEffect;
    }

    /**
     * Fetches the ItemFactory for this rod.
     * NOTE: Creating an ItemStack from this factory will not add the necessary NBT to identify the rod. Use {@link #create()} instead.
     */
    public ItemFactory getFactory() {
        return this.factory;
    }

    /**
     * Creates an ItemStack of this rod, with the necessary NBT to identify it.
     */
    @Override
    public @NotNull ItemStack create() {
        ItemStack item = factory.createItem();
        if (this.unbreakable) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setUnbreakable(true);
                item.setItemMeta(meta);
            }
        }
        NBT.modify(item, nbt -> {
            ReadWriteNBT emfCompound = nbt.getOrCreateCompound(NbtKeys.EMF_COMPOUND);
            emfCompound.setString(NbtKeys.EMF_ROD_ID, getId());
        });
        return item;
    }

    @Override
    public @NotNull List<Rarity> getAllowedRarities() {
        return this.allowedRarities;
    }

    @Override
    public @NotNull List<Fish> getAllowedFish() {
        return this.allowedFish;
    }

    public @Nullable EMFRecipe<?> getRecipe() {
        return this.recipe;
    }

}
