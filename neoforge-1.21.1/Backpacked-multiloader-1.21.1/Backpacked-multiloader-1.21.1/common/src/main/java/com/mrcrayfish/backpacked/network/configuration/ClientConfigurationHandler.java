package com.mrcrayfish.backpacked.network.configuration;

import com.mrcrayfish.backpacked.client.ClientRegistry;
import com.mrcrayfish.backpacked.network.message.MessageSyncBackpacks;
import com.mrcrayfish.framework.api.network.FrameworkResponse;

import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

/**
 * Author: MrCrayfish
 */
public class ClientConfigurationHandler
{
    public static FrameworkResponse handleMessageSyncBackpacks(MessageSyncBackpacks message, Consumer<Runnable> executor)
    {
        CountDownLatch latch = new CountDownLatch(1);
        executor.accept(() -> {
            ClientRegistry.instance().updateBackpacks(message.backpacks());
            latch.countDown();
        });
        try
        {
            latch.await();
        }
        catch(InterruptedException e)
        {
            return FrameworkResponse.error("Failed to update backpacks");
        }
        return FrameworkResponse.SUCCESS;
    }
}
