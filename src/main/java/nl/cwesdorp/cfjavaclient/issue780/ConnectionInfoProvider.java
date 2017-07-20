package nl.cwesdorp.cfjavaclient.issue780;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Created by Chris.
 */
public class ConnectionInfoProvider {

    private Properties props;

    public ConnectionInfoProvider() {
        InputStream in = getClass().getClassLoader().getResourceAsStream("api.properties");
        props = new Properties();
        try {
            props.load(in);
        } catch (IOException ex) {
            throw new IllegalStateException("resource file does not exists");
        }
    }

    public String getApiEndpoint() {
        return props.getProperty("endpoint");
    }

    public String getOrganization() {
        return props.getProperty("organization");
    }

    public String getSpace() {
        return props.getProperty("space");
    }

    public String getRefreshToken() {
        return props.getProperty("refreshToken");
    }

}
