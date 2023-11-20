package ca.ibodrov.mica.server.ui;

import com.google.common.io.ByteStreams;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@WebServlet("/api/mica/swagger.json")
public class SwaggerServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setStatus(200);
        resp.setHeader("Content-Type", "application/json");
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        var in = cl.getResourceAsStream("ca/ibodrov/mica/server/swagger/swagger.json");
        assert in != null;
        ByteStreams.copy(in, resp.getOutputStream());
    }
}
