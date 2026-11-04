import com.oheers.fish.fishing.simulator.presentation.UiProfile;
import com.oheers.fish.fishing.simulator.presentation.gui.*;
import com.oheers.fish.fishing.simulator.SimulatorModule;
import org.bukkit.*;import org.bukkit.entity.*;
import org.bukkit.inventory.*;import org.bukkit.event.inventory.*;import org.bukkit.event.player.*;
import net.kyori.adventure.text.Component;
import java.util.*;
public class SimulatorHudGuiSmoke {
    static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
    public static void main(String[] args){
        var player=new Player(UUID.randomUUID());Bukkit.users.put(player.getUniqueId(),player);
        var module=new SimulatorModule(); var gui=new HudSettingsGui(module);
        var buttons=new EnumMap<UiProfile,HudGuiSettings.Button>(UiProfile.class);
        int i=0;for(UiProfile profile:UiProfile.values()){
            buttons.put(profile,new HudGuiSettings.Button(10+i++,Material.PAPER,Component.empty(),List.of(),false));
        }
        gui.configure(new HudGuiSettings(true,Component.empty(),27,Material.GRAY_STAINED_GLASS_PANE,
            Component.empty(),Sound.UI_BUTTON_CLICK,buttons));
        check(gui.open(player)&&gui.openSessions()==1,"GUI opens with one session");
        Inventory old=player.getOpenInventory().getTopInventory();
        int changes=module.changes;
        var click=new InventoryClickEvent(player.getOpenInventory(),player,13,ClickType.LEFT);
        gui.click(click);
        check(click.isCancelled() && module.profile==UiProfile.CINEMATIC && module.changes==changes+1,"mapped click switches profile");
        for(var type:List.of(ClickType.SHIFT_LEFT,ClickType.NUMBER_KEY,ClickType.DOUBLE_CLICK)){
            var cheat=new InventoryClickEvent(player.getOpenInventory(),player,14,type);
            gui.click(cheat);check(cheat.isCancelled() && module.changes==changes+1,"unsafe click escaped: "+type);
        }
        var bottom=new InventoryClickEvent(player.getOpenInventory(),player,28,ClickType.LEFT);
        gui.click(bottom);check(bottom.isCancelled() && module.changes==changes+1,"bottom inventory click escaped");
        var drag=new InventoryDragEvent(player.getOpenInventory());gui.drag(drag);check(drag.isCancelled(),"drag escaped");
        check(gui.open(player),"GUI replace opened");
        var stale=new InventoryClickEvent(new InventoryView(old),player,10,ClickType.LEFT);
        gui.click(stale);check(stale.isCancelled() && module.changes==changes+1,"stale session was active");
        gui.close(new InventoryCloseEvent(old));check(gui.openSessions()==1,"old close deleted new session");
        gui.quit(new PlayerQuitEvent(player));check(gui.openSessions()==0,"quit did not free session");
        gui.configure(HudGuiSettings.disabled());check(!gui.open(player),"disabled GUI should not open");
        System.out.println("PASS Phase 10B: slot-owned GUI, safe clicks, drag, stale close, quit, and disabled gate");
    }
}
