package org.example.Classes;

import org.example.Annotations.Inject;

public class E {
    public A aField;

    @Inject
    public E(A afield) {
        this.aField = afield;
    }
}
