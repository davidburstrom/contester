# Strip the breakpoint calls from release builds
-assumenosideeffects class io.github.davidburstrom.contester.ConTesterBreakpoint {
    static <methods>;
}

