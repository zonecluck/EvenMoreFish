package org.evenmorefish.fish.addons.item;

import com.oheers.fish.api.addons.ItemAddon;
import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.api.Type;
import net.Indyuce.mmoitems.api.item.mmoitem.MMOItem;
import org.bukkit.inventory.ItemStack;
import java.util.Locale;

public class MMOItemsItemAddon extends ItemAddon {

    @Override
    public ItemStack getItemStack(String id) {
        String[] splitMaterialValue = id == null ? new String[0] : id.split(":", -1);
        if (splitMaterialValue.length != 2 || splitMaterialValue[0].isBlank() || splitMaterialValue[1].isBlank()) {
            getLogger().severe(() -> String.format(
                "Incorrect format for MMOItemsItemAddon, use %s:type:id. Got %s",
                getIdentifier(),
                id
            ));
            return null;
        }

        MMOItems plugin = MMOItems.plugin;
        if (plugin == null || !plugin.isEnabled()) return null;
        Type type = plugin.getTypes().get(splitMaterialValue[0].trim().toUpperCase(Locale.ROOT));
        if (type == null) {
            getLogger().info(() -> String.format("Could not obtain MMOItems item %s", id));
            return null;
        }
        MMOItem item = plugin.getMMOItem(type, splitMaterialValue[1].trim().toUpperCase(Locale.ROOT));
        if (item == null) {
            getLogger().info(() -> String.format("Could not obtain MMOItems item %s", id));
            return null;
        }

        ItemStack built = item.newBuilder().build();
        return built == null ? null : built.clone();
    }

    @Override
    public String getPluginName() {
        return "MMOItems";
    }

    @Override
    public String getAuthor() {
        return "FireML";
    }

    @Override
    public String getIdentifier() {
        return "MMOITEMS";
    }

}
