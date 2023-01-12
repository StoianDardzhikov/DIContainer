package org.example;


import org.example.Classes.Lazy;

public class Main {
    public static void main(String[] args) throws Exception {
        Container container = new Container();
        Lazy lazy = container.getInstance(Lazy.class);
        lazy.loaded.printHi();
    }
}
