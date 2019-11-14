package lab5;

import javafx.util.Pair;

public class GetDataMsg {

    private Pair<String, Integer> msg;

    public GetDataMsg(Pair<String, Integer> msg){
        this.msg = msg;
    }

    public String getUrl(){
        return msg.getKey();
    }

    public Integer getValue(){
        return msg.getValue();
    }
}
