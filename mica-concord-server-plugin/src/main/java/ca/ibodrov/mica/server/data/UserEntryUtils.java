package ca.ibodrov.mica.server.data;

import com.walmartlabs.concord.server.user.UserEntry;

public final class UserEntryUtils {

    public static UserEntry systemUser() {
        return user("system");
    }

    public static UserEntry user(String username) {
        return new UserEntry(null, username, null, null, null, null, null, null, false, null, false);
    }

    private UserEntryUtils() {
    }
}
