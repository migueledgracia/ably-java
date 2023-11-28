package io.ably.lib.transport;

import io.ably.lib.types.*;
import io.ably.lib.util.AgentHeaderCreator;
import io.ably.lib.util.Log;
import io.ably.lib.util.PlatformAgentProvider;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import java.util.List;

public interface ITransport {

    String TAG = ITransport.class.getName();

    interface Factory {
        /**
         * Obtain and instance of this transport based on the specified options.
         */
        ITransport getTransport(TransportParams transportParams, ConnectionManager connectionManager);
    }

    enum Mode {
        clean,
        resume,
        recover
    }

    class TransportParams {
        protected ClientOptions options;
        protected String host;
        protected int port;
        protected String connectionKey;
        protected String connectionSerial;
        protected Mode mode;
        protected boolean heartbeats;
        private final PlatformAgentProvider platformAgentProvider;

        public TransportParams(ClientOptions options, PlatformAgentProvider platformAgentProvider) {
            this.options = options;
            this.platformAgentProvider = platformAgentProvider;
            heartbeats = true; /* default to requiring Ably heartbeats */
        }

        public String getHost() {
            return host;
        }

        public int getPort() {
            return port;
        }

        public ClientOptions getClientOptions() {
            return options;
        }

        public Param[] getConnectParams(Param[] baseParams) {
            List<Param> paramList = new ArrayList<Param>(Arrays.asList(baseParams));
            paramList.add(new Param(Defaults.ABLY_VERSION_PARAM, Defaults.ABLY_VERSION));
            paramList.add(new Param("format", (options.useBinaryProtocol ? "msgpack" : "json")));
            if(!options.echoMessages)
                paramList.add(new Param("echo", "false"));
            if(connectionKey != null) {
                mode = Mode.resume;
                paramList.add(new Param("resume", connectionKey));
                if(connectionSerial != null)
                    paramList.add(new Param("connectionSerial", connectionSerial));
            } else if(options.recover != null && !options.recover.isEmpty()) { // RTN16k
                mode = Mode.recover;
                RecoveryKeyContext recoveryKeyContext = RecoveryKeyContext.decode(options.recover);
                if (recoveryKeyContext != null) {
                    paramList.add(new Param("recover", recoveryKeyContext.getConnectionKey()));
                }
            }
            if(options.clientId != null)
                paramList.add(new Param("clientId", options.clientId));
            if(!heartbeats)
                paramList.add(new Param("heartbeats", "false"));

            if(options.transportParams != null) {
                paramList.addAll(Arrays.asList(options.transportParams));
            }
            paramList.add(new Param(Defaults.ABLY_AGENT_PARAM, AgentHeaderCreator.create(options.agents, platformAgentProvider)));
            Log.d(TAG, "getConnectParams: params = " + paramList);
            return paramList.toArray(new Param[paramList.size()]);
        }
    }

    interface ConnectListener {
        void onTransportAvailable(ITransport transport);
        void onTransportUnavailable(ITransport transport, ErrorInfo reason);
    }

    /**
     * Initiate a connection attempt; the transport will be activated,
     * and attempt to remain connected, until disconnect() is called.
     * @throws AblyException
     */
    void connect(ConnectListener connectListener);

    /**
     * Close this transport.
     */
    void close();

    /**
     * Send a message on the channel
     * @param msg
     * @throws IOException
     */
    void send(ProtocolMessage msg) throws AblyException;

    void receive(ProtocolMessage msg) throws AblyException;

    /**
     * Get connection URL
     * @return
     */
    String getURL();

    String getHost();

}
