package rs.ac.bg.etf.kdp;

import java.io.Serializable;

/** Interface for synchronization through a shared Linda tuple space. */
public interface Linda extends Serializable {

    public void out(String[] tuple);

    public void in(String[] tuple);

    public boolean inp(String[] tuple);

    public void rd(String[] tuple);

    public boolean rdp(String[] tuple);

    public void eval(String name, Runnable thread);
}
