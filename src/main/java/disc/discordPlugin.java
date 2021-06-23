package disc;

import arc.Core;
import arc.Events;

import arc.files.Fi;
import arc.struct.ObjectMap;
import arc.util.CommandHandler;
import arc.util.Log;
import arc.util.Strings;
import mindustry.game.EventType;
import mindustry.gen.Call;

import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.mod.Plugin;
import org.javacord.api.DiscordApi;
import org.javacord.api.DiscordApiBuilder;
import org.javacord.api.entity.channel.Channel;
import org.javacord.api.entity.channel.TextChannel;
import org.javacord.api.entity.message.MessageBuilder;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.entity.permission.Role;

//json
import org.json.JSONObject;
import org.json.JSONTokener;


import java.awt.*;
import java.lang.Thread;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Optional;

import static disc.discConstants.*;
import static disc.utilmethods.*;

public class discordPlugin extends Plugin {
    private final Long CDT = 300L;
    private final String FileNotFoundErrorMessage = "File not found: config\\mods\\settings.json";
    private JSONObject alldata;
    private JSONObject data; //token, channel_id, role_id
    private DiscordApi api = null;
    private HashMap<Long, String> cooldowns = new HashMap<Long, String>(); //uuid


    private boolean invalidConfig = false;
    private ObjectMap<String, Role> discRoles = new ObjectMap<>();
    private ObjectMap<String, TextChannel> discChannels = new ObjectMap<>();

    //private JSONObject config;
    private String totalPath;
    private String servername;


    //register event handlers and create variables in the constructor
    public discordPlugin() {
        //getting the config file:
        Fi path = Core.settings.getDataDirectory().child(diPath);
        totalPath = path.child(fileName).absolutePath();

        JSONObject config = null;

        if(path.exists()){
            Log.info("<disc> PATH EXISTS");
            String pureJSON = path.child(totalPath).readString();
            config = new JSONObject(new JSONTokener(pureJSON));
            if(!config.has("version")){
                makeSettingsFile();
            }else if(config.getInt("version") < VERSION){
                Log.info("<disc> configfile: VERSION");
                makeSettingsFile();
            }
        }else{
            makeSettingsFile();
        }

        if(config == null || invalidConfig) return;

        readSettingsFile(config);




        try {
            String pureJson = Core.settings.getDataDirectory().child("mods/settings.json").readString();
            alldata = new JSONObject(new JSONTokener(pureJson));
            if (!alldata.has("in-game")){
                System.out.println("[ERR!] discordplugin: settings.json has an invalid format!\n");
                //this.makeSettingsFile("settings.json");
                return;
            } else {
                data = alldata.getJSONObject("in-game");
            }
        } catch (Exception e) {
            if (e.getMessage().contains(FileNotFoundErrorMessage)){
                System.out.println("[ERR!] discordplugin: settings.json file is missing.\nBot can't start.");
                //this.makeSettingsFile("settings.json");
                return;
            } else {
                System.out.println("[ERR!] discordplugin: Init Error");
                e.printStackTrace();
                return;
            }
        }
        try {
            api = new DiscordApiBuilder().setToken(alldata.getString("token")).login().join();
        }catch (Exception e){
            if (e.getMessage().contains("READY packet")){
                System.out.println("\n[ERR!] discordplugin: invalid token.\n");
            } else {
                e.printStackTrace();
            }
        }
        BotThread bt = new BotThread(api, Thread.currentThread(), alldata.getJSONObject("discord"));
        bt.setDaemon(false);
        bt.start();

        //live chat
        if (data.has("live_chat_channel_id")) {
            TextChannel tc = getTextChannel(api, data.getString("live_chat_channel_id"));
            if (tc != null) {
                Events.on(EventType.PlayerChatEvent.class, event -> {
                    tc.sendMessage("**" + event.player.name.replace('*', '+') + "**: " + event.message);
                });
            }
        }
    }

    //register commands that run on the server
    @Override
    public void registerServerCommands(CommandHandler handler){

    }

    //register commands that player can invoke in-game
    @Override
    public void registerClientCommands(CommandHandler handler){
        if (api != null) {
            handler.<Player>register("d", "<text...>", "Sends a message to discord.", (args, player) -> {

                if (!data.has("dchannel_id")) {
                    player.sendMessage("[scarlet]This command is disabled.");
                } else {
                    TextChannel tc = getTextChannel(api, data.getString("dchannel_id"));
                    if (tc == null) {
                        player.sendMessage("[scarlet]This command is disabled.");
                        return;
                    }
                    tc.sendMessage(player.name + " *@mindustry* : " + args[0]);
                    Call.sendMessage(player.name + "[sky] to @discord[]: " + args[0]);
                }

            });

            handler.<Player>register("gr", "[player] [reason...]", "Report a griefer by id (use '/gr' to get a list of ids)", (args, player) -> {
                //https://github.com/Anuken/Mindustry/blob/master/core/src/io/anuke/mindustry/core/NetServer.java#L300-L351
                if (!(data.has("channel_id") && data.has("role_id"))) {
                    player.sendMessage("[scarlet]This command is disabled.");
                    return;
                }


                for (Long key : cooldowns.keySet()) {
                    if (key + CDT < System.currentTimeMillis() / 1000L) {
                        cooldowns.remove(key);
                        continue;
                    } else if (player.uuid() == cooldowns.get(key)) {
                        player.sendMessage("[scarlet]This command is on a 5 minute cooldown!");
                        return;
                    }
                }

                if (args.length == 0) {
                    StringBuilder builder = new StringBuilder();
                    builder.append("[orange]List or reportable players: \n");
                    for (Player p : Groups.player) {
                        if (p.admin() || p.con == null) continue;

                        builder.append("[lightgray] ").append(p.name).append("[accent] (#").append(p.id).append(")\n");
                    }
                    player.sendMessage(builder.toString());
                } else {
                    Player found = null;
                    if (args[0].length() > 1 && args[0].startsWith("#") && Strings.canParseInt(args[0].substring(1))) {
                        int id = Strings.parseInt(args[0].substring(1));
                        for (Player p: Groups.player){
                            if (p.id == id){
                                found = p;
                                break;
                            }
                        }
                    } else {
                        for (Player p: Groups.player){
                            if (p.name.equalsIgnoreCase(args[0])){
                                found = p;
                                break;
                            }
                        }
                    }
                    if (found != null) {
                        if (found.admin()) {
                            player.sendMessage("[scarlet]Did you really expect to be able to report an admin?");
                        } else if (found.team() != player.team()) {
                            player.sendMessage("[scarlet]Only players on your team can be reported.");
                        } else {
                            TextChannel tc = getTextChannel(api, data.getString("channel_id"));
                            Role r = getRole(api, data.getString("role_id"));
                            if (tc == null || r == null) {
                                player.sendMessage("[scarlet]This command is disabled.");
                                return;
                            }
                            //send message
                            if (args.length > 1) {
                                new MessageBuilder()
                                        .setEmbed(new EmbedBuilder()
                                                .setTitle("Potential griefer online")
                                                .setDescription(r.getMentionTag())
                                                .addField("name", found.name)
                                                .addField("reason", args[1])
                                                .setColor(Color.ORANGE)
                                                .setFooter("Reported by " + player.name))
                                        .send(tc);
                            } else {
                                new MessageBuilder()
                                        .setEmbed(new EmbedBuilder()
                                                .setTitle("Potential griefer online")
                                                .setDescription(r.getMentionTag())
                                                .addField("name", found.name)
                                                .setColor(Color.ORANGE)
                                                .setFooter("Reported by " + player.name))
                                        .send(tc);
                            }
                            Call.sendMessage(found.name + "[sky] is reported to discord.");
                            cooldowns.put(System.currentTimeMillis() / 1000L, player.uuid());
                        }
                    } else {
                        player.sendMessage("[scarlet]No player[orange] '" + args[0] + "'[scarlet] found.");
                    }
                }
            });
        }
    }

    private void readSettingsFile(JSONObject obj){
        if(obj.has("token")){
            try {
                api = new DiscordApiBuilder().setToken(obj.getString("token")).login().join();
                Log.info("<disc> Valid token");
            }catch (Exception e){
                if (e.getMessage().contains("READY packet")){
                    Log.info("<disc> invalid token");
                } else {
                    e.printStackTrace();
                }
                invalidConfig = true;
                return;
            }
        }else{
            invalidConfig = true;
            return;
        }

        if(obj.has("channel_ids")){
            JSONObject temp = obj.getJSONObject("channel_ids");
            for(String field : temp.keySet()){
                discChannels.put(field, getTextChannel(api, obj.getString(field)));
            }
        }

        if(obj.has("role_ids")){
            JSONObject temp = obj.getJSONObject("role_ids");
            for(String field : temp.keySet()){
                discRoles.put(field, getRole(api, obj.getString(field)));
            }
        }

        if(obj.has("servername")){
            servername = obj.getString("servername");
        }else{
            servername = null;
        }

        Log.info("<disc> config loaded");
    }


    private void makeSettingsFile(){
        Log.info("<disc> CREATING JSON FILE");
        Fi directory = Core.settings.getDataDirectory().child(diPath);
        if(!directory.isDirectory()){
            directory.mkdirs();
        }

        JSONObject config = new JSONObject();
        config.put("info", "more info available on: https://github.com/J-VdS/DiscordPlugin");
        config.put("version", VERSION);

        config.put("servername", "name of your server - can be empty");

        config.put("token", "put your token here");

        JSONObject channels = new JSONObject();
        channels.put("dchannel_id", "messages using /d will be send to this channel - can be empty");
        channels.put("channel_id", "id of the channel where /gr reports will appear - can be empty");
        channels.put("live_chat_channel_id", "id of the channel where live chat will appear - can be empty");

        config.put("channel_ids", channels);

        JSONObject roles = new JSONObject();
        String[] discordRoles = {
                "closeServer_role_id",
                "gameOver_role_id",
                "changeMap_role_id",
                "serverDown_role_id"
        };
        for (String field : discordRoles){
            roles.put(field, "");
        }

        config.put("role_ids", roles);

        Log.info("<disc> Creating config.json");
        try{
            Files.write(Paths.get(totalPath), config.toString().getBytes());
        }catch (Exception e){
            Log.info("<disc> Failed to create config.json");
        }
    }
}