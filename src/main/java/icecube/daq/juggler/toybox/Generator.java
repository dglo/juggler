package icecube.daq.juggler.toybox;

import java.nio.ByteBuffer;

/**
 * Payload generator.
 */
interface Generator
{
    /**
     * Generate next payload.
     *
     * @return payload bytes (or <tt>null</tt> if there are no more payloads)
     */
    ByteBuffer generate();

    /**
     * Are there more payloads to be generated?
     *
     * @return <tt>true</tt> if there are more payloads
     */
    boolean isGenerating();

    /**
     * Reset generator.
     */
    void reset();

    /**
     * Stop the generator.
     */
    void stop();
}
