package ca.ibodrov.mica.server.reports;

import ca.ibodrov.mica.api.model.PartialEntity;

public interface Report<O extends Report.Options> {

    PartialEntity run(O options);

    interface Options {
    }
}
