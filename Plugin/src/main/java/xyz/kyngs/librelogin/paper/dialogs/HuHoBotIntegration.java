package xyz.kyngs.librelogin.paper.dialogs;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.PluginManager;
import xyz.kyngs.librelogin.api.database.User;
import xyz.kyngs.librelogin.common.config.ConfigurationKeys;
import xyz.kyngs.librelogin.paper.PaperLibreLogin;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Optional, reflection-based HuHoBot bridge. LibreLogin remains loadable without HuHoBot. */
public final class HuHoBotIntegration implements Listener {
    private static final String EVENT_CLASS = "cn.huohuas001.huhobot.spigot.api.BotCustomCommand";
    private static final long CODE_LIFETIME_MILLIS = 10 * 60 * 1000L;

    private final PaperLibreLogin plugin;
    private final DialogManager manager;
    private final Map<String, PendingCode> pendingCodes = new ConcurrentHashMap<>();
    private final Map<UUID, String> candidateMembers = new ConcurrentHashMap<>();

    public HuHoBotIntegration(PaperLibreLogin plugin, DialogManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    public void register() {
        org.bukkit.plugin.Plugin huhobotPlugin = Bukkit.getPluginManager().getPlugin("HuHoBot");
        if (huhobotPlugin == null) {
            plugin.getLogger().info("HuHoBot 未检测到：未安装或插件名称不是 HuHoBot");
            return;
        }
        if (!huhobotPlugin.isEnabled()) {
            plugin.getLogger().warn("HuHoBot 已检测到，但当前未启用，群找回密码事件不会生效");
            return;
        }
        try {
            Class<?> eventClass = Class.forName(EVENT_CLASS, false, huhobotPlugin.getClass().getClassLoader());
            PluginManager pluginManager = Bukkit.getPluginManager();
            EventExecutor executor = (listener, event) -> handleEvent(event);
            @SuppressWarnings("unchecked") Class<? extends Event> typed = (Class<? extends Event>) eventClass;
            pluginManager.registerEvent(typed, this, EventPriority.NORMAL, executor, plugin.getBootstrap());
            plugin.getLogger().info("HuHoBot password recovery integration enabled");
        } catch (Exception e) {
            plugin.getLogger().warn("HuHoBot detected, but password recovery integration could not be registered: " + e.getMessage());
        }
    }

    public void begin(Player player) {
        User user = plugin.getDatabaseProvider().getByUUID(player.getUniqueId());
        if (user == null || !user.isRegistered()) {
            player.sendMessage(Component.text("§c" + plugin.getMessages().getRawMessage("error-not-registered")));
            return;
        }
        String code;
        do {
            code = String.format("%06d", (int) (Math.random() * 1_000_000));
        } while (pendingCodes.containsKey(code));
        pendingCodes.put(code, new PendingCode(player.getUniqueId(), System.currentTimeMillis() + CODE_LIFETIME_MILLIS));
        plugin.getAuthorizationProvider().getPasswordResetCache().put(player.getUniqueId(), code);

        manager.showHuHoBotResetRequestDialog(player, code, CODE_LIFETIME_MILLIS / 60_000L);
    }

    /** Promotes the QQ member that last attempted recovery after a normal login succeeds. */
    public void recordSuccessfulLogin(Player player) {
        String candidate = candidateMembers.remove(player.getUniqueId());
        if (candidate == null) return;
        invokeHuHoBot("promoteInitialMemberOpenId", player.getName(), candidate);
    }

    private void handleEvent(Event event) {
        try {
            Method getCommand = event.getClass().getMethod("getCommand");
            String command = String.valueOf(getCommand.invoke(event));
            if (!isPasswordResetCommand(command)) return;

            @SuppressWarnings("unchecked") List<String> params = (List<String>) event.getClass().getMethod("getParam").invoke(event);
            String code = params == null || params.isEmpty() ? "" : params.get(0).trim();
            Object responseTarget = event;
            if (code.isEmpty()) {
                respond(responseTarget, "用法：/找回密码 <验证码>");
                cancel(event);
                return;
            }
            PendingCode pending = pendingCodes.remove(code);
            if (pending == null || pending.expiresAt < System.currentTimeMillis()) {
                respond(responseTarget, "验证码无效或已过期");
                cancel(event);
                return;
            }
            Player player = Bukkit.getPlayer(pending.uuid);
            User user = player == null ? null : plugin.getDatabaseProvider().getByUUID(pending.uuid);
            if (player == null || user == null || !user.isRegistered()) {
                respond(responseTarget, "玩家不在线或账号不存在");
                cancel(event);
                return;
            }
            String groupOpenId = getDataString(event, "group_openid");
            String memberOpenId = getAuthorMemberOpenId(event);
            if (groupOpenId == null || memberOpenId == null || !isBoundToPlayer(groupOpenId, memberOpenId, player)) {
                respond(responseTarget, "当前QQ未绑定此游戏账号，无法找回密码。");
                cancel(event);
                return;
            }
            String initialMember = getInitialMemberOpenId(player);
            if (initialMember == null) {
                invokeHuHoBot("promoteInitialMemberOpenId", player.getName(), memberOpenId);
                initialMember = memberOpenId;
            }
            if (!initialMember.equals(memberOpenId)) {
                candidateMembers.put(player.getUniqueId(), memberOpenId);
                respond(responseTarget, "当前QQ不是此账号的初始绑定QQ，请先使用密码登录一次后再找回密码。");
                cancel(event);
                return;
            }
            respond(responseTarget, "验证码验证成功，请返回游戏设置新密码。");
            cancel(event);
            plugin.delay(() -> manager.showHuHoBotPasswordResetDialog(player), 100);
        } catch (Exception e) {
            if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                plugin.getLogger().warn("HuHoBot password recovery event failed: " + e.getMessage());
            }
        }
    }

    private void respond(Object event, String message) throws Exception {
        event.getClass().getMethod("respone", String.class, String.class).invoke(event, message, "text");
    }

    private boolean isBoundToPlayer(String groupOpenId, String memberOpenId, Player player) {
        Object result = invokeHuHoBot("getBoundPlayerNames", groupOpenId, memberOpenId);
        if (!(result instanceof List<?> names)) return false;
        String lastNickname = plugin.getDatabaseProvider().getByUUID(player.getUniqueId()).getLastNickname();
        return names.stream().anyMatch(name -> name != null && (name.toString().equalsIgnoreCase(player.getName())
                || lastNickname != null && name.toString().equalsIgnoreCase(lastNickname)));
    }

    private String getInitialMemberOpenId(Player player) {
        Object result = invokeHuHoBot("getInitialMemberOpenId", player.getName());
        return result == null ? null : String.valueOf(result);
    }

    private String getDataString(Event event, String key) throws Exception {
        Object data = event.getClass().getMethod("getData").invoke(event);
        Object value = data.getClass().getMethod("getString", String.class).invoke(data, key);
        return value == null || String.valueOf(value).isBlank() ? null : String.valueOf(value);
    }

    private String getAuthorMemberOpenId(Event event) throws Exception {
        Object data = event.getClass().getMethod("getData").invoke(event);
        Object author = data.getClass().getMethod("getJSONObject", String.class).invoke(data, "author");
        if (author == null) return null;
        Object member = author.getClass().getMethod("getString", String.class).invoke(author, "member_openid");
        if (member == null) member = author.getClass().getMethod("getString", String.class).invoke(author, "openId");
        return member == null || String.valueOf(member).isBlank() ? null : String.valueOf(member);
    }

    private Object invokeHuHoBot(String method, Object... args) {
        try {
            org.bukkit.plugin.Plugin huhobot = Bukkit.getPluginManager().getPlugin("HuHoBot");
            if (huhobot == null || !huhobot.isEnabled()) return null;
            Class<?>[] types = new Class<?>[args.length];
            for (int i = 0; i < args.length; i++) types[i] = String.class;
            return huhobot.getClass().getMethod(method, types).invoke(huhobot, args);
        } catch (Exception e) {
            if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                plugin.getLogger().warn("HuHoBot recovery API call failed: " + method + ": " + e.getMessage());
            }
            return null;
        }
    }

    private boolean isPasswordResetCommand(String command) {
        if (command.equals("找回密码") || command.equalsIgnoreCase("password-reset")
                || command.equalsIgnoreCase("huhobot-password-reset")) return true;
        try {
            Object config = Bukkit.getPluginManager().getPlugin("HuHoBot").getClass()
                    .getMethod("getConfig").invoke(Bukkit.getPluginManager().getPlugin("HuHoBot"));
            Object configured = config.getClass().getMethod("getString", String.class)
                    .invoke(config, "officialBot.commands.password-reset");
            return configured != null && command.equalsIgnoreCase(String.valueOf(configured));
        } catch (Exception ignored) {
            return false;
        }
    }

    private void cancel(Event event) throws Exception {
        if (event instanceof org.bukkit.event.Cancellable cancellable) cancellable.setCancelled(true);
    }

    private record PendingCode(UUID uuid, long expiresAt) {}
}
