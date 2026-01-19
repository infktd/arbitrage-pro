package com.arbitragepro;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class ArbitrageProTest {
    public static void main(String[] args) throws Exception {
        ExternalPluginManager.loadBuiltin(ArbitrageProPlugin.class);
        RuneLite.main(args);
    }
}
