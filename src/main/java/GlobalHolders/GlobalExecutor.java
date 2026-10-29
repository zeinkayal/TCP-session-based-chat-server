package GlobalHolders;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GlobalExecutor {
    public static final ExecutorService executor= Executors.newVirtualThreadPerTaskExecutor();
    public GlobalExecutor(){}
}
