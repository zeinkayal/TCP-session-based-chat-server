package GlobalHolders;

import Manager.Session;

import java.util.concurrent.ConcurrentHashMap;

public class GlobalMapHolder {
    public static final ConcurrentHashMap<String, Session> SHARED_MAP= new ConcurrentHashMap<>();
    public GlobalMapHolder(){}

}
