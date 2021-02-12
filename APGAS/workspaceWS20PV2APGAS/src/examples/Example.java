package examples;

import java.io.Serializable;

public class Example implements Serializable {
    private static final long serialVersionUID = 12345L;
    private int x;

    public Example(int x) {
        this.x = x;
    }

    public int getX() {
        return x;
    }
}
