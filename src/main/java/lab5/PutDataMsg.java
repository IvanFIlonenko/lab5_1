package lab5;

import javafx.util.Pair;

public class PutDataMsg {

    private Pair<String,Pair<Integer, Integer>> msg;

    public PutDataMsg(Pair<String,Pair<Integer, Integer>> msg){
        this.msg = msg;
    }

    public String getUrl(){
        return msg.getKey();
    }

    public Integer getRequestNumber(){
        return msg.getValue().getKey();
    }

    public Integer getTime(){
        return msg.getValue().getValue();
    }
}
