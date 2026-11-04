package com.oheers.fish.gui.guis;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import vn.ledat.platform.api.LeDatPlatformApi;
import vn.ledat.platform.api.LeDatPlatformProvider;
import vn.ledat.platform.api.dialog.DialogButton;
import vn.ledat.platform.api.dialog.DialogDisplayMode;
import vn.ledat.platform.api.dialog.DialogInputSpec;
import vn.ledat.platform.api.dialog.DialogInputType;

import java.util.UUID;
import java.util.function.Consumer;

/** Loaded separately only when the optional public Platform provider is enabled. */
final class PlatformJournalFilterAdapter implements JournalFilterController.Bridge {
    private static final String TOKEN = "emf-journal-token";
    private final Plugin owner;
    private final LeDatPlatformApi api;

    PlatformJournalFilterAdapter(Plugin owner) {
        this.owner = owner;
        this.api = LeDatPlatformProvider.get();
    }

    @Override
    public boolean open(Player player, UUID token, JournalFilterState initial, boolean rarityMenu,
                        Consumer<JournalFilterState> save, Runnable cancel, Consumer<String> invalid) {
        Component title = Component.text("Bộ lọc nhật ký cá");
        Component queryLabel = Component.text(rarityMenu ? "Tìm tên phẩm chất" : "Tìm tên cá");
        if (api.forms().canOpen(player)) {
            var form = api.forms().custom(owner, "emf_journal_filter")
                    .title(title).context(TOKEN, token.toString())
                    .input("query", queryLabel, "Để trống để xem tất cả", initial.query())
                    .dropdown("sort", Component.text("Sắp xếp"), JournalFilterState.SORT_LABELS, initial.sortIndex())
                    .toggle("caught_only", Component.text("Chỉ hiện mục đã câu"), initial.caughtOnly())
                    .submit(Component.text("Áp dụng"), click -> {
                        if (foreignUi(player, token)) return;
                        try { save.accept(JournalFilterState.bedrockValues(click.inputs())); }
                        catch (IllegalArgumentException failure) { invalid.accept("Bộ lọc không hợp lệ."); }
                    }).onClose(close -> cancel.run());
            return form.open(player).opened();
        }
        if (!api.dialogs().canUseNative(player)) return false;
        var sort = DialogInputSpec.builder("sort", DialogInputType.SINGLE_OPTION).label(Component.text("Sắp xếp"));
        var types = com.oheers.fish.utils.sort.SortType.values();
        for (int index = 0; index < types.length; index++) {
            sort.option(types[index].name(), Component.text(JournalFilterState.SORT_LABELS.get(index)), types[index] == initial.sort());
        }
        var dialog = api.dialogs().dialog(owner, "emf_journal_filter")
                .title(title).context(TOKEN, token.toString()).mode(DialogDisplayMode.NATIVE_ONLY)
                .input(DialogInputSpec.text("query", queryLabel).initial(initial.query()).maxLength(128).build())
                .input(sort.build())
                .input(DialogInputSpec.builder("caught_only", DialogInputType.BOOLEAN)
                        .label(Component.text("Chỉ hiện mục đã câu")).initialBoolean(initial.caughtOnly()).build())
                .button(DialogButton.confirm(Component.text("Áp dụng"), Component.empty(), click -> {
                    if (foreignUi(player, token)) return;
                    try { save.accept(JournalFilterState.nativeValues(click.inputs())); }
                    catch (IllegalArgumentException failure) { invalid.accept("Bộ lọc không hợp lệ."); }
                }))
                .button(DialogButton.cancel(Component.text("Quay lại"), Component.empty(), click -> cancel.run()))
                .onClose(close -> cancel.run()).build();
        api.dialogs().open(player, dialog);
        return true;
    }

    @Override
    public boolean foreignUi(Player player, UUID token) {
        var dialog = api.dialogs().session(player.getUniqueId());
        var form = api.forms().session(player.getUniqueId());
        return api.guis().session(player.getUniqueId()) != null
                || (dialog != null && !token.toString().equals(dialog.context(TOKEN)))
                || (form != null && !token.toString().equals(form.context(TOKEN)));
    }

    @Override
    public boolean confirmed(Player player, UUID token) {
        var dialog = api.dialogs().session(player.getUniqueId());
        var form = api.forms().session(player.getUniqueId());
        return (dialog != null && dialog.nativeDialog() && token.toString().equals(dialog.context(TOKEN)))
                || (form != null && token.toString().equals(form.context(TOKEN)));
    }

    @Override
    public void cleanup(UUID player, UUID token) {
        var dialog = api.dialogs().session(player);
        if (dialog != null && token.toString().equals(dialog.context(TOKEN))) api.dialogs().cleanup(player);
        var form = api.forms().session(player);
        if (form != null && token.toString().equals(form.context(TOKEN))) api.forms().cleanup(player);
    }
}
