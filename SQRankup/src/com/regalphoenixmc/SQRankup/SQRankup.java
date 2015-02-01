
package com.regalphoenixmc.SQRankup;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import net.milkbowl.vault.VaultEco;
import net.milkbowl.vault.permission.Permission;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import com.greatmancode.craftconomy3.Cause;
import com.greatmancode.craftconomy3.Common;
import com.greatmancode.craftconomy3.tools.interfaces.Loader;
import com.regalphoenixmc.SQRankup.CC3Wrapper.CC3Currency;

public class SQRankup extends JavaPlugin implements Listener {

	public static Permission permission = null;
	public static SQRankup instance;
	public static VaultEco vaultEco;
	public static int MULTIPLIER = 1;
	public static FileConfiguration config;
	public static HashMap<String, Integer> infamyCostMap = new HashMap<String, Integer>();
	public static HashMap<String, Integer> creditMap = new HashMap<String, Integer>();
	public static HashMap<String, Integer> infamyGainMap = new HashMap<String, Integer>();
	public static HashMap<String, List<String>> rankTree = new HashMap<String, List<String>>();
	public static HashMap<String, int[]> itemNames = new HashMap<String, int[]>();
	public static Common craftconomy;

	public void onEnable() {

		saveDefaultConfig();
		getServer().getPluginManager().registerEvents(this, this);
		setupPermissions();
		instance = this;
		config = instance.getConfig();
		Database.setUp();
		loadRanks();
		MULTIPLIER = config.getInt("multiplier");
		new NotifierTask().runTaskTimer(instance, 12000, 12000);
		Plugin plugin = Bukkit.getPluginManager().getPlugin("Craftconomy3");
		if (plugin != null) {
			craftconomy = (Common) ((Loader) plugin).getCommon();
		}
	}

	public void loadRanks() {

		Set<String> names = config.getConfigurationSection("ranks").getKeys(false);
		System.out.println(names.toString());
		for (String name : names) {
			System.out.println(name);
			infamyCostMap.put(name, config.getInt("ranks." + name + ".infamycost"));
			creditMap.put(name, config.getInt("ranks." + name + ".credits"));
			infamyGainMap.put(name, config.getInt("rank." + name + ".infamygain"));
			rankTree.put(name, config.getStringList("ranks." + name + ".next"));
		}
		System.out.println(infamyCostMap.toString());
		System.out.println(creditMap.toString());
		System.out.println(rankTree.toString());
	}

	@EventHandler
	public void playerLogin(PlayerLoginEvent e) {

		notifyPlayerOfMultiplier(e.getPlayer());
	}

	public static SQRankup instance() {

		return instance;
	}

	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

		if (cmd.getName().equalsIgnoreCase("rankuprefresh") && sender.hasPermission("SQRankup.refresh")) {
			config = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "config.yml"));
			refresh();
			sender.sendMessage("Rankup Multiplier Refreshed");
			return true;
		}

		if (cmd.getName().equalsIgnoreCase("rankupmultiplier") && sender.hasPermission("SQRankup.multiplier")) {
			MULTIPLIER = Integer.parseInt(args[0]);
			instance.getConfig().set("multiplier", MULTIPLIER);
			saveConfig();
			String suffix = "";
			if (args.length > 1) {
				suffix += " by " + args[1];
			}
			sender.sendMessage("Rankup Multiplier set to " + MULTIPLIER + suffix);
			return true;
		}

		if ((cmd.getName().equalsIgnoreCase("rankup")) && ((sender instanceof Player))) {
			Player p = (Player) sender;
			String rank = getRank(p);
			String nextRank = getNextRank(rank);
			if (nextRank == null) {
				p.sendMessage(ChatColor.RED + "You are unable to rank up.");
				return true;
			}
			if (nextRank.equals("")) {
				List<String> nextRanks = rankTree.get(rank);
				if (args.length == 0) {
					sender.sendMessage(ChatColor.RED + " You need to choose a rank path! Your avaliable choices are:");
					for (String r : nextRanks) {
						sender.sendMessage(ChatColor.GOLD + "    " + r);
					}
					return true;
				}

				if (nextRanks.contains(args[0].toLowerCase())) {
					nextRank = args[0];
				} else {
					sender.sendMessage("Invalid rank. Type /rankup to see your choices");
					return true;
				}

			}
			if (rank.equalsIgnoreCase("settler")) {
				sender.sendMessage(ChatColor.RED + "To rank up to settler, you must apply to the server on our minecraft forums thread");
				sender.sendMessage(ChatColor.GOLD + "http://tinyurl.com/starquestapps");
				return true;
			}

			int moneyRequirement = getMonetaryCost(nextRank);
			int killsRequirement = getKillRequirement(nextRank);
			double killsFound = CC3Wrapper.getBalance(p.getName(), CC3Currency.INFAMY);
			double moneyFound = CC3Wrapper.getBalance(p.getName(), CC3Currency.CREDITS);
			if ((killsFound >= killsRequirement) && (moneyFound >= moneyRequirement)) {
				getServer().broadcastMessage(ChatColor.RED + sender.getName() + " has ranked up to " + nextRank.toString().toLowerCase() + "!");
				permission.playerAddGroup(p, nextRank);
				permission.playerRemoveGroup(p, rank);
				CC3Wrapper.withdraw(moneyRequirement, p.getName(), CC3Currency.CREDITS, Cause.PLUGIN, "Rankup withdrawl");
				CC3Wrapper.withdraw(killsRequirement, p.getName(), CC3Currency.INFAMY, Cause.PLUGIN, "Rankup withdrawl");
			} else {
				sender.sendMessage("Current money: " + moneyFound + " Required Money: " + moneyRequirement + " Current Kills: " + killsFound
						+ " Required Kills: " + killsRequirement);
			}
			return true;
		}

		if ((cmd.getName().equalsIgnoreCase("addapp")) && (sender.hasPermission("SQRankup.addApplication"))) {
			String rank = getRank(getServer().getOfflinePlayer(args[0]));
			String nextRank = getNextRank(rank);
			if (args.length >= 1) {
				getServer().broadcastMessage(ChatColor.RED + args[0] + " has ranked up to settler!");
				getServer().broadcastMessage(
						ChatColor.RED + "Are you a " + ChatColor.GREEN + "Refugee" + ChatColor.RED + "? Rank up to " + ChatColor.DARK_GREEN + "Settler"
								+ ChatColor.RED + " like " + args[0] + " did!");
				getServer().broadcastMessage(
						ChatColor.RED + "Visit " + ChatColor.BLUE + "http://tinyurl.com/starquestapps" + ChatColor.RED + " to apply for Settler rank!");

				permission.playerAddGroup(null, getServer().getOfflinePlayer(args[0]), nextRank);
				permission.playerRemoveGroup(null, getServer().getOfflinePlayer(args[0]), rank);
				if (args.length >= 2) {
					Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "money give " + args[1] + " 10000");
					getServer().broadcastMessage(
							ChatColor.GOLD + args[1] + ChatColor.RED + " brought " + args[0] + " to the server and earned 10000 for doing so!");
				}
				return true;
			}
			sender.sendMessage("Needs an argument.");
			return false;
		}

		return false;
	}

	public static void refresh() {

		MULTIPLIER = instance.getConfig().getInt("multiplier");
	}

	@EventHandler
	public void onPlayerKill(PlayerDeathEvent event) {

		// if it's a suicide
		if (event.getEntity().getKiller() == event.getEntity())
			return;

		if (event.getEntity().getKiller() instanceof Player) {
			Player killer = (Player) event.getEntity().getKiller();
			Player killed = event.getEntity();
			boolean cooldown = Database.isInCooldown(killer, killed);
			System.out.println(cooldown);
			if (!cooldown) {
				int infamy = rankToKills(killed);
				CC3Wrapper.deposit(infamy, killer.getName(), CC3Currency.INFAMY, Cause.PLUGIN, "Rankup Kill");
				killer.sendMessage(ChatColor.RED + "You were awarded " + infamy + " infamy for that kill. You now have "
						+ CC3Wrapper.getBalance(killer.getName(), CC3Currency.INFAMY) + " infamy");
				Database.addKill(killer, killed);
			} else {
				killer.sendMessage(ChatColor.RED + "You already killed that player in the last 20 minutes! Lay off for a bit...");
			}
		}
	}

	private int rankToKills(Player player) {

		int i = 0;
		String[] groups = permission.getPlayerGroups(null, getServer().getOfflinePlayer(player.getUniqueId()));
		System.out.println(Arrays.toString(groups));
		for (String p : groups) {
			if (infamyGainMap.containsKey(p.toLowerCase())) {
				i = infamyGainMap.get(p.toLowerCase());
			}
		}
		int cost = i < 0 ? i : i * MULTIPLIER;
		return cost;
	}

	// method to get the next rank on the rank structure
	public String getNextRank(String rank) {

		for (String test : rankTree.keySet()) {
			List<String> ranks = rankTree.get(test);
			if (ranks.size() > 1) {
				return "";
			} else if (ranks == null || ranks.size() == 0) {
				return null;
			} else {
				return ranks.get(0).toUpperCase();
			}
		}
		return null;
	}

	// method for getting monetary cost of rankup
	public int getMonetaryCost(String rank) {

		for (String test : creditMap.keySet()) {
			if (test.toLowerCase().equals(rank.toLowerCase())) {
				return creditMap.get(rank);
			}
		}

		return 0;
	}

	public int getKillRequirement(String rank) {

		for (String test : infamyCostMap.keySet()) {
			if (test.toLowerCase().equals(rank.toLowerCase())) {
				return infamyCostMap.get(rank);
			}
		}

		return 0;
	}

	public String getRank(OfflinePlayer player) {

		String[] allGroups = permission.getPlayerGroups(null, player);
		for (String p : allGroups) {
			List<String> ranks = config.getStringList("ranks");
			for (String configName : ranks) {
				if (p.equalsIgnoreCase(configName)) {
					return configName.toString();
				}
			}

		}
		return null;

	}

	private boolean setupPermissions() {

		RegisteredServiceProvider<Permission> permissionProvider = getServer().getServicesManager().getRegistration(Permission.class);
		if (permissionProvider != null) {
			permission = (Permission) permissionProvider.getProvider();
		}
		return permission != null;
	}

	private void notifyPlayerOfMultiplier(Player player) {

		if (MULTIPLIER > 1) {
			player.sendMessage(ChatColor.GOLD + "There is a x" + MULTIPLIER + " multiplier on all kill values");
		}
	}

}