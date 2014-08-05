/*
 * Copyright (C) 2014 Harry Devane
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.fadecloud.dropparty;

import com.vexsoftware.votifier.model.VotifierEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * https://www.github.com/Harry5573OP
 *
 * @author Harry5573OP
 */
public class FadeDropParty extends JavaPlugin implements Listener, CommandExecutor {
      
      @Getter
      private static FadeDropParty plugin_instance = null;
      
      private String toggleOnMessage = null;
      private String toggleOffMessage = null;
      private List<String> DPStartMessages = null;
      private List<String> DPEndMessages = null;
      private String DPWarningMessage = null;
      private List<Integer> DPWarningIntervals = null;
      private int DPVotesNeeded = 0;
      private int DPLengthSeconds = 0;
      private HashSet<DropPartyReward> rewards = null;
      
      private Random random = new Random();
      private List<UUID> playersWithDPDisabled = new ArrayList<>();
      
      @Getter
      private static int currentVotes = 0;
      
      @Override
      public void onEnable() {
            plugin_instance = this;
            
            getLogger().info("=[ Plugin version " + getDescription().getVersion() + " starting ]=");
            
            saveDefaultConfig();
            reload();
            
            getServer().getPluginManager().registerEvents(this, this);
            getCommand("dp").setExecutor(this);

            getLogger().info("=[ Plugin version " + getDescription().getVersion() + " started ]=");
      }

      @Override
      public void onDisable() {
            getLogger().info("=[ Plugin version " + getDescription().getVersion() + " shutting down ]=");

            getLogger().info("=[ Plugin version " + getDescription().getVersion() + " shutdown ]=");
      }

      @Override
      public boolean onCommand(CommandSender sender, Command command, String commandLabel, String[] args) {
            if (!(sender instanceof Player)) {
                  getLogger().info("That is not a valid console command.");
                  return true;
            }

            if (args.length < 1) {
                  return true;
            }
            
            if (args[0].toLowerCase().startsWith("on")) {
                  ((Player) sender).sendMessage(this.toggleOnMessage);
                  playersWithDPDisabled.remove(((Player) sender).getUniqueId());
                  return true;
            }

            if (args[0].toLowerCase().startsWith("off")) {
                  ((Player) sender).sendMessage(this.toggleOffMessage);
                  playersWithDPDisabled.add(((Player) sender).getUniqueId());
                  return true;
            }

            if (args[0].toLowerCase().startsWith("status")) {
                  ((Player) sender).sendMessage(ChatColor.GRAY + "---------------");
                  ((Player) sender).sendMessage("Votes needed for DP to begin: " + ChatColor.GOLD + currentVotes);
                  ((Player) sender).sendMessage(ChatColor.GRAY + "---------------");
                  return true;
            }
            
            if (args[0].toLowerCase().startsWith("reload")) {
                  if (!((Player) sender).isOp()) {
                        ((Player) sender).sendMessage(ChatColor.RED + "You do not have permission to do this");
                        return true;
                  }
                  ((Player) sender).sendMessage(ChatColor.GREEN + "Configuration reloaded.");
                  reload();
                  return true;
            }
            
            return false;
      }

      @EventHandler(priority = EventPriority.HIGHEST)
      public void onVote(VotifierEvent event) {
            currentVotes--;

            if (DPWarningIntervals.contains(currentVotes)) {
                  getServer().broadcastMessage(DPWarningMessage.replace("[VOTES]", String.valueOf(currentVotes)));
            }

            if (currentVotes <= 0) {
                  currentVotes = DPVotesNeeded;
                  //Cancel incase old DP running.
                  getServer().getScheduler().cancelTasks(plugin_instance);

                  getServer().getScheduler().runTaskLater(this, new Runnable() {
                        public void run() {
                              new dropPartyThread().runTaskTimerAsynchronously(plugin_instance, 20L, 20L);
                        }
                  }, 1L);
            }
      }

      class DropPartyReward {

            private final double chance;
            private final HashSet<String> rewardCommands;
            
            public DropPartyReward(double chance, List<String> commands) {
                  this.chance = chance;
                  this.rewardCommands = new HashSet<>();
                  this.rewardCommands.addAll(commands);
            }
      }
      
      private void reload() {
            reloadConfig();
            
            toggleOnMessage = translateColorCodes(getConfig().getString("messages.toggleOn"));
            toggleOffMessage = translateColorCodes(getConfig().getString("messages.toggleOff"));
            
            DPStartMessages = new ArrayList<>();
            for (String startMessage : getConfig().getStringList("messages.dpStartMessage")) {
                  DPStartMessages.add(translateColorCodes(startMessage));
            }
            
            DPEndMessages = new ArrayList<>();
            for (String endMessage : getConfig().getStringList("messages.dpEndMessage")) {
                  DPEndMessages.add(translateColorCodes(endMessage));
            }
            
            DPWarningMessage = translateColorCodes(getConfig().getString("messages.warningMessage"));
            DPWarningIntervals = getConfig().getIntegerList("warningIntervals");
            DPVotesNeeded = getConfig().getInt("votesNeeded");
            currentVotes = DPVotesNeeded;
            DPLengthSeconds = getConfig().getInt("dpLengthSeconds");
            
            rewards = new HashSet<>();
            
            for (String reward : getConfig().getConfigurationSection("rewards").getKeys(false)) {
                  rewards.add(new DropPartyReward(getConfig().getDouble("rewards." + reward + ".chance"), getConfig().getStringList("rewards." + reward + ".commands")));
            }
      }
      
      private String translateColorCodes(String message) {
            return ChatColor.translateAlternateColorCodes('&', message);
      }
      
      class dropPartyThread extends BukkitRunnable {
            
            int currentTime = DPLengthSeconds + 1;
            
            public dropPartyThread() {
                  start();
            }

            @Override
            public void run() {
                  if (currentTime <= 0) {
                        return;
                  }

                  currentTime--;

                  final Player[] onlinePlayers = getServer().getOnlinePlayers();
                  for (Player player : onlinePlayers) {
                        if (!playersWithDPDisabled.contains(player.getUniqueId())) {
                              DropPartyReward chosenReward = getRandomReward();

                              getServer().getScheduler().runTask(plugin_instance, new Runnable() {
                                    public void run() {
                                          for (String command : chosenReward.rewardCommands) {
                                                getServer().dispatchCommand(Bukkit.getConsoleSender(), command.replace("{playername}", player.getName()));
                                          }
                                    }
                              });
                        }
                  }

                  if (currentTime <= 0) {
                        end();
                        return;
                  }
            }
            
            private void start() {
                  for (String startMessage : DPStartMessages) {
                        getServer().broadcastMessage(startMessage.replace("[VOTES]", String.valueOf(DPVotesNeeded)));
                  }
            }
            
            private void end() {
                  for (String endMessage : DPEndMessages) {
                        getServer().broadcastMessage(endMessage.replace("[VOTES]", String.valueOf(DPVotesNeeded)));
                  }
                  
                  cancel();
            }
      }
      
      private DropPartyReward getRandomReward() {
            int total = 0;
            
            List<DropPartyReward> items = new ArrayList<>();
            
            for (DropPartyReward item : this.rewards) {
                  items.add(item);
                  total += item.chance;
            }
            
            int r = Math.max(1, random.nextInt(total));
            int count = 0;
            int i = 0;
            
            while (count < r) {
                  count += items.get(i++).chance;
            }
            return items.get(i - 1);
      }
}
