package de.fernunihagen.dna.jkn.scalephant.network.packages.request;

import java.io.OutputStream;
import java.nio.ByteBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.fernunihagen.dna.jkn.scalephant.network.NetworkConst;
import de.fernunihagen.dna.jkn.scalephant.network.NetworkPackageDecoder;
import de.fernunihagen.dna.jkn.scalephant.network.NetworkPackageEncoder;
import de.fernunihagen.dna.jkn.scalephant.network.packages.NetworkRequestPackage;
import de.fernunihagen.dna.jkn.scalephant.network.routing.RoutingHeader;

public class DisconnectRequest implements NetworkRequestPackage {
	
	/**
	 * The Logger
	 */
	private final static Logger logger = LoggerFactory.getLogger(DisconnectRequest.class);

	
	@Override
	public void writeToOutputStream(final short sequenceNumber, final OutputStream outputStream) {

		try {
			// Write body length
			final long bodyLength = 0;
			
			// Unrouted package
			final RoutingHeader routingHeader = new RoutingHeader(false);
			NetworkPackageEncoder.appendRequestPackageHeader(sequenceNumber, bodyLength, routingHeader,
					getPackageType(), outputStream);

		} catch (Exception e) {
			logger.error("Got exception while converting package into bytes", e);
		}
	}
	
	/**
	 * Decode the encoded package into a object
	 * 
	 * @param encodedPackage
	 * @return
	 */
	public static DisconnectRequest decodeTuple(final ByteBuffer encodedPackage) {
		final boolean decodeResult = NetworkPackageDecoder.validateRequestPackageHeader(encodedPackage, NetworkConst.REQUEST_TYPE_DISCONNECT);
		
		if(decodeResult == false) {
			logger.warn("Unable to decode package");
			return null;
		}
		
		if(encodedPackage.remaining() != 0) {
			logger.error("Some bytes are left after encoding: " + encodedPackage.remaining());
		}
		
		return new DisconnectRequest();
	}

	@Override
	public byte getPackageType() {
		return NetworkConst.REQUEST_TYPE_DISCONNECT;
	}

	@Override
	public boolean equals(Object obj) {
		if(obj instanceof DisconnectRequest) {
			return true;
		}
		
		return false;
	}
}
