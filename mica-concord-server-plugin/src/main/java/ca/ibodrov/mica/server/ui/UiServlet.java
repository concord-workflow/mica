package ca.ibodrov.mica.server.ui;

import ca.ibodrov.mica.common.SpaServlet;

import javax.servlet.annotation.WebServlet;

@WebServlet("/mica/*")
public class UiServlet extends SpaServlet {

    public UiServlet() {
        super("META-INF/mica-ui.checksums.cvs",
                "META-INF/mica-ui/",
                "index.html");
    }
}
