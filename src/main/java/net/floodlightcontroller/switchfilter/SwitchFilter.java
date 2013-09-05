package net.floodlightcontroller.switchfilter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IOFSwitch.PortChangeType;
import net.floodlightcontroller.core.IOFSwitchListener;
import net.floodlightcontroller.core.ImmutablePort;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryService;

import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple Floodlight Module that drops all PacketIn messages from switches with
 * DPID matching given values.
 * 
 * To add a drop filter, just call filterMatchSet.add(String s) in this module's
 * init function.
 * 
 * @author Henrique Rodrigues
 * 
 */
public class SwitchFilter implements IOFSwitchListener, IOFMessageListener,
		IFloodlightModule {

	protected IFloodlightProviderService floodlightProvider;
	protected static Logger logger;
	protected Set<String> filterMatchSet;
	protected ILinkDiscoveryService lldService;

	public SwitchFilter() {
	}

	@Override
	public String getName() {
		return SwitchFilter.class.getSimpleName();
	}

	@Override
	public boolean isCallbackOrderingPrereq(OFType type, String name) {
		return false;
	}

	@Override
	public boolean isCallbackOrderingPostreq(OFType type, String name) {
		return false;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		return null;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		return null;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();
		l.add(IFloodlightProviderService.class);
		l.add(ILinkDiscoveryService.class);
		return l;
	}

	@Override
	public void init(FloodlightModuleContext context)
			throws FloodlightModuleException {
		floodlightProvider = context
				.getServiceImpl(IFloodlightProviderService.class);
		lldService = context
				.getServiceImpl(ILinkDiscoveryService.class);
		logger = LoggerFactory.getLogger(SwitchFilter.class);
		
		filterMatchSet = new ConcurrentSkipListSet<String>();

		// Filter all switches with names matching "^11.*"
		filterMatchSet.add("^11.*");
	}

	@Override
	public void startUp(FloodlightModuleContext context)
			throws FloodlightModuleException {
		// Register for OF message updates
		floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
		floodlightProvider.addOFMessageListener(OFType.PACKET_OUT, this);
		
		// Register for switch status updates
		floodlightProvider.addOFSwitchListener(this);
	}

	@Override
	public net.floodlightcontroller.core.IListener.Command receive(
			IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {

		String switchDPID = sw.getStringId();
		for (String match : this.filterMatchSet) {
			if (switchDPID.matches(match)) {
				if (msg.getType() == OFType.PACKET_IN) {
					logger.debug(
							"Packet in from switch {}, {}, discarding it...",
							sw.getStringId(), msg.toString());
				} else if (msg.getType() == OFType.PACKET_OUT) {
					// There is no way to filter packet outs here, because
					// Floodlight's PacketOut processing doesn't check the
					// result of this call. We need to do that at the source
					// module or change Floodlight's core logic.
					logger.warn("Packet out to switch {}, {}!",
							sw.getStringId(), msg.toString());
				}
				return Command.STOP;
			}
		}
		return Command.CONTINUE;
	}

	@Override
	public void switchAdded(long switchId) {
		IOFSwitch sw = floodlightProvider.getSwitch(switchId);
		logger.info("SwitchFilter: switch " + sw.getStringId() + " added");
		
		// Here we disable LLDP learning for all switches with names 
    	// matching one of the entries in this.filterMatchSet
		if (lldService != null) {
			for (String match : this.filterMatchSet) {
				if (sw.getStringId().matches(match)) {
		        	logger.info("disabling LLDP for switch" + sw.getStringId());
		        	for (short port : sw.getEnabledPortNumbers())
		        		lldService.AddToSuppressLLDPs(switchId, port);
				}
	        }
		}
	}

	@Override
	public void switchRemoved(long switchId) {
		// no-op
		
	}

	@Override
	public void switchActivated(long switchId) {
		// no-op
		
	}

	@Override
	public void switchPortChanged(long switchId, ImmutablePort port,
			PortChangeType type) {
		// no-op
	}

	@Override
	public void switchChanged(long switchId) {
		// no-op
	}

}
