package com.healthylauncher.block;

interface IShellService {
    void destroy() = 16777114;
    int run(String command) = 1;
}
