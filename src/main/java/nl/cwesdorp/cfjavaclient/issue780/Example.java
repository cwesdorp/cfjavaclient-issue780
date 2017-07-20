package nl.cwesdorp.cfjavaclient.issue780;

import org.cloudfoundry.operations.DefaultCloudFoundryOperations;
import org.cloudfoundry.operations.applications.PushApplicationRequest;
import org.cloudfoundry.operations.applications.StartApplicationRequest;
import org.cloudfoundry.operations.services.BindServiceInstanceRequest;
import org.cloudfoundry.operations.services.CreateServiceInstanceRequest;
import org.cloudfoundry.reactor.DefaultConnectionContext;
import org.cloudfoundry.reactor.TokenProvider;
import org.cloudfoundry.reactor.client.ReactorCloudFoundryClient;
import org.cloudfoundry.reactor.tokenprovider.RefreshTokenGrantTokenProvider;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Random;

/**
 * Created by Chris.
 */
public class Example {

    public static void main(String[] args) {
        new Example().run();
    }

    private ConnectionInfoProvider cip;

    private void run() {
        cip = new ConnectionInfoProvider();

        // ConnectionContext is the target API endpoint for CF
        DefaultConnectionContext connectionCtx = getDefaultConnectionCtx();

        // The token provider is used to pass the refresh token to the CF client
        TokenProvider tokenProvider = getTokenProvider();

        // push and try to start
        String appName = pushApplicationReactor(connectionCtx, tokenProvider);
        createAndBindService(connectionCtx, tokenProvider, appName);
        startApp(connectionCtx, tokenProvider, appName);
    }

    private String pushApplicationReactor(DefaultConnectionContext connectionCtx,
                                          TokenProvider tokenProvider) {
        System.out.println("Push application");

        ReactorCloudFoundryClient cloudFoundryClient = ReactorCloudFoundryClient.builder()
                .connectionContext(connectionCtx)
                .tokenProvider(tokenProvider)
                .build();

        DefaultCloudFoundryOperations opsClient = DefaultCloudFoundryOperations.builder()
                .cloudFoundryClient(cloudFoundryClient)
                .organization(cip.getOrganization())
                .space(cip.getSpace())
                .build();

        /** We need to modify the application a bit to prevent the CF Cloud Cache to interfere **/
        String appName = "sample-" + System.currentTimeMillis();
        Path workingCopy;

        try {
            URL appFile = getClass().getClassLoader().getResource("SampleAppA.mpk");
            Path app = Paths.get(appFile.toURI());

            workingCopy = Paths.get(".", appName + ".mpk");
            Files.copy(app, workingCopy);

            URI zip = URI.create("jar:" + workingCopy.toUri());
            try (FileSystem fs = FileSystems.newFileSystem(zip, new HashMap<String, String>())) {
                Path randomFile = fs.getPath("theme/random.file");
                try (OutputStream out = Files.newOutputStream(randomFile, StandardOpenOption.CREATE) ) {
                    byte[] twoMbRandom = new byte[1024 * 1024 * 2];
                    new Random().nextBytes(twoMbRandom);
                    out.write(twoMbRandom);
                }

                Path randomFile2 = fs.getPath("theme/random2.file");
                try (OutputStream out = Files.newOutputStream(randomFile2, StandardOpenOption.CREATE) ) {
                    byte[] randomData = new byte[1024 * 1024 * 2 + 1024 * 8 + 5];
                    new Random().nextBytes(randomData);
                    out.write(randomData);
                }
            }


        } catch (IOException | URISyntaxException e) {
            e.printStackTrace();
            return null;
        }

        opsClient.applications()
                .push(PushApplicationRequest.builder()
                        .path(workingCopy)
                        .name(appName)
                        .buildpack("https://github.com/mendix/cf-mendix-buildpack")
                        .memory(512)
                        .instances(1)
                        .noStart(true)
                        .build())
                .doOnError(t -> System.out.printf("doOnError: %s - %s\n", t.getClass().getName(), t.getMessage()))
                .block();
        System.out.println("Done - pushed " + appName);

        return appName;
    }

    private void createAndBindService(DefaultConnectionContext connectionCtx, TokenProvider provider,
                                      String appName) {
        System.out.println("createAndBindService");

        ReactorCloudFoundryClient cloudFoundryClient = ReactorCloudFoundryClient.builder()
                .connectionContext(connectionCtx)
                .tokenProvider(provider)
                .build();

        DefaultCloudFoundryOperations opsClient = DefaultCloudFoundryOperations.builder()
                .cloudFoundryClient(cloudFoundryClient)
                .organization(cip.getOrganization())
                .space(cip.getSpace())
                .build();

        String serviceNameInstance = "database-" + appName;
        opsClient.services()
                .createInstance(CreateServiceInstanceRequest.builder()
                        .serviceInstanceName(serviceNameInstance)
                        .serviceName("elephantsql")
                        .planName("turtle")
                        .build())
                .block();

        System.out.println("service created");

        opsClient.services()
                .bind(BindServiceInstanceRequest.builder()
                        .applicationName(appName)
                        .serviceInstanceName(serviceNameInstance)
                        .build())
                .block();

        System.out.println("Service bound");
    }

    private void startApp(DefaultConnectionContext connectionCtx, TokenProvider provider,
                          String appName) {
        System.out.println("startApp");

        ReactorCloudFoundryClient cloudFoundryClient = ReactorCloudFoundryClient.builder()
                .connectionContext(connectionCtx)
                .tokenProvider(provider)
                .build();

        DefaultCloudFoundryOperations opsClient = DefaultCloudFoundryOperations.builder()
                .cloudFoundryClient(cloudFoundryClient)
                .organization(cip.getOrganization())
                .space(cip.getSpace())
                .build();

        opsClient.applications().start(StartApplicationRequest.builder()
                .name(appName)
                .build())
                .block();
        System.out.println("App " + appName + " started");
    }

    private DefaultConnectionContext getDefaultConnectionCtx() {
        return DefaultConnectionContext.builder()
                .apiHost(cip.getApiEndpoint())
                .build();
    }

    private TokenProvider getTokenProvider() {
        assert cip.getRefreshToken() != null;

        return RefreshTokenGrantTokenProvider.builder()
                .token(cip.getRefreshToken())
                .build();
    }

}
