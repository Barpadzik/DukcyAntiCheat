package pl.barpad.duckyanticheat.utils;

import org.bukkit.entity.Player;

public class PermissionBypass {

    /**
     * Checks whether the player has any bypass permission for DuckyAC.
     *
     * @param player The player to check
     * @return true if the player has bypass permissions, false otherwise
     */
    public static boolean hasBypass(Player player) {
        return player.hasPermission("duckyac.bypass") || player.hasPermission("duckyac.*");
    }
}