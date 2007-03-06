package icecube.daq.juggler.component;

import icecube.daq.io.PayloadOutputEngine;
import icecube.daq.io.PayloadTransmitChannel;

/**
 * Hack to update component when a new transmit channel is created.
 *
 * TODO: This should probably go away!
 */
public interface DAQOutputHack
{
    /**
     * Callback method invoked when a new transmit channel is associated
     * with the specified output engine.
     *
     * @param outEngine output engine
     * @param xmitChan new transmit channel
     *
     * @throws DAQCompException if there is a problem
     */
    void createdTransmitChannel(PayloadOutputEngine outEngine,
                                PayloadTransmitChannel xmitChan)
        throws DAQCompException;
}
