package Classes;

import org.example.Annotations.Inject;
import org.example.Annotations.Named;
import org.example.Initializer;

public class FSI implements Initializer {
    @Inject @Named
    public String email;

    @Override
    public void init() {
        email = "mailto:" + email;
    }
}
