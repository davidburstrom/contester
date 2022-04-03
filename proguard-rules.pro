# Strip the breakpoint calls from release builds
-assumenosideeffects class com.github.davidburstrom.contester.ConTesterBreakpoint {
    static <methods>;
}

