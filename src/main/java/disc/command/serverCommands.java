package disc.command;


import java.util.Set;
import java.util.Iterator;

import arc.Core;
import arc.Events;
import mindustry.game.EventType.*;
import mindustry.Vars;
import mindustry.core.GameState;
import mindustry.game.Team;

import rhino.*;

import org.javacord.api.entity.permission.Role;
import org.javacord.api.event.message.MessageCreateEvent;
import org.javacord.api.listener.message.MessageCreateListener;

import disc.discordPlugin;

import static disc.utilmethods.*;


public class serverCommands implements MessageCreateListener {
    final String commandDisabled = "This command is disabled.";

    private discordPlugin mainData;

    public serverCommands(discordPlugin _data){
        this.mainData = _data;
    }

    @Override
    public void onMessageCreate(MessageCreateEvent event) {
        String[] incoming_msg = event.getMessageContent().split("\\s+");
        Role adminRole = mainData.discRoles.get("admin_role_id");

        switch (incoming_msg[0]){
            case ";gameover":
                if (adminRole == null){
                    if (event.isPrivateMessage()) return;
                    event.getChannel().sendMessage(commandDisabled);
                    return;
                }
                if (!hasPermission(adminRole, event)) return;
                // ------------ has permission --------------
                if (Vars.state.is(GameState.State.menu)) {
                    return;
                }
                Events.fire(new GameOverEvent(Team.derelict));
                break;
            case ";js":
            if (adminRole == null){
                if (event.isPrivateMessage()) return;
                event.getChannel().sendMessage(commandDisabled);
                return;
            }
            if (!hasPermission(adminRole, event)) return;
            // ------------ has permission --------------
            if (Vars.state.is(GameState.State.menu)) {
                return;
            }
            Scriptable scope = Vars.mods.getScripts().scope;
            Thread jsThread = new Thread(()->{
                String in = "";
                String out = null;
                Context context = Context.enter();
                for(int i = 1; i < incoming_msg.length; i++){
                    in+=(incoming_msg[i] + " ");
                }
                try{
                    Object o = context.evaluateString(scope, in, "console.js", 1);
                    if(o instanceof NativeJavaObject n) o = n.unwrap();
                    if(o == null) o = "null";
                    else if(o instanceof Undefined) o = "undefined";
                    out = o.toString();
                    if(out == null){out = "null";}
                }catch(Throwable t){
                    out = t.getClass().getSimpleName() + (t.getMessage() == null ? "" : ": " + t.getMessage());
                };
                Context.exit();
                event.getChannel().sendMessage("[gold]" + out);
                Thread.currentThread().stop();
            }, "js");
            jsThread.start();
            break;
            case ";stopjs":
            if (adminRole == null){
                if (event.isPrivateMessage()) return;
                event.getChannel().sendMessage(commandDisabled);
                return;
            }
            if (!hasPermission(adminRole, event)) return;
            // ------------ has permission --------------
            if (Vars.state.is(GameState.State.menu)) {
                return;
            }
            Set<Thread> threadSet = Thread.getAllStackTraces().keySet();
			Iterator<Thread> iterator = threadSet.iterator();
			int count = 0;
            Thread nextq = null; //next already exists
			while(iterator.hasNext()){
				nextq = iterator.next();
				if(nextq.getName() == "js"){
					nextq.stop();
					count++;
				}
			}
            event.getChannel().sendMessage(String.format("[gold]Stopped @ JS threads", count));
            break;
            default:
                break;
        }
    }
}