package lab5;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.japi.pf.ReceiveBuilder;

import java.util.HashMap;
import java.util.Map;

public class Storage extends AbstractActor {

    private HashMap<String, Map<Integer, Integer>> data = new HashMap<>();

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create().
                match(GetDataMsg.class, msg -> {

                    if (data.containsKey(msg.getUrl()) && data.get(msg.getUrl()).containsKey(msg.getValue())) {
                        getSender().tell(data.get(msg.getUrl()).get(msg.getValue()), ActorRef.noSender());
                    } else {
                        getSender().tell(-1, ActorRef.noSender());
                    }
                }).match(PutDataMsg.class, msg ->{
                    Map<Integer, Integer> temp;
                    if (data.containsKey(msg.getUrl())) {
                        temp = data.get(msg.getUrl());
                    } else {
                        temp = new HashMap<>();
                    }
                    temp.put(msg.getRequestNumber(), msg.getTime());
                    data.put(msg.getUrl(),temp);
        })
                .build();
    }
}
