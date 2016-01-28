package com.HP.pipelinemaker.impl;

import static com.hp.of.lib.instr.ActionFactory.createAction;
import static com.hp.of.lib.instr.InstructionFactory.createMutableInstruction;
import static com.hp.of.lib.match.MatchFactory.createMatch;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;

import com.hp.of.ctl.ControllerService;
import com.hp.of.lib.OpenflowException;
import com.hp.of.lib.ProtocolVersion;
import com.hp.of.lib.dt.BufferId;
import com.hp.of.lib.dt.DataPathId;
import com.hp.of.lib.dt.TableId;
import com.hp.of.lib.err.ErrorType;
import com.hp.of.lib.instr.ActionType;
import com.hp.of.lib.instr.InstrMutableAction;
import com.hp.of.lib.instr.Instruction;
import com.hp.of.lib.instr.InstructionType;
import com.hp.of.lib.match.MFieldExperimenter;
import com.hp.of.lib.match.Match;
import com.hp.of.lib.match.MutableMatch;
import com.hp.of.lib.match.OxmBasicFieldType;
import com.hp.of.lib.mp.MBodyMutableTableFeatures;
import com.hp.of.lib.mp.MBodyTableFeatures;
import com.hp.of.lib.mp.MBodyTableFeatures.MutableArray;
import com.hp.of.lib.mp.MpBodyFactory;
import com.hp.of.lib.mp.MultipartBody;
import com.hp.of.lib.mp.MultipartType;
import com.hp.of.lib.msg.FlowModCommand;
import com.hp.of.lib.msg.FlowModFlag;
import com.hp.of.lib.msg.MessageFactory;
import com.hp.of.lib.msg.MessageFuture;
import com.hp.of.lib.msg.MessageType;
import com.hp.of.lib.msg.OfmError;
import com.hp.of.lib.msg.OfmFlowMod;
import com.hp.of.lib.msg.OfmMutableFlowMod;
import com.hp.of.lib.msg.OfmMutableMultipartRequest;
import com.hp.of.lib.msg.OpenflowMessage;
import com.hp.of.lib.msg.TableFeatureFactory;
import com.hp.of.lib.msg.TableFeaturePropType;

import static com.hp.of.lib.match.FieldFactory.createBasicField;
import static com.hp.of.lib.match.FieldFactory.createExperimenterField;

import com.hp.sdn.misc.SdnLog;
import com.hp.util.ip.EthernetType;

public class PipelineMakerManager extends Thread {

	private DataPathId datapathId;
	private ControllerService controller;

	private static final Logger log = SdnLog.APP.getLogger();

	public PipelineMakerManager(DataPathId datapathId) {
		this.datapathId = datapathId;
	}

	public void setDatapathId(DataPathId datapathId) {
		this.datapathId = datapathId;
	}

	public void setControllerService(ControllerService controller) {
		this.controller = controller;
	}

	@Override
	public void run() {

		log.info("Getting PipelineInfo for DataPath: " + datapathId.toString());

		/* Create a Table Features Request Message */
		OfmMutableMultipartRequest mr = (OfmMutableMultipartRequest) MessageFactory.create(ProtocolVersion.V_1_3,
				MessageType.MULTIPART_REQUEST, MultipartType.TABLE_FEATURES);
		OpenflowMessage msg = mr.toImmutable();
		try {

			/* Send the Table Features Request Message with an empty body */
			MessageFuture msgReply = controller.send(msg, datapathId);

			/* Wait for the Table Features Reply Message */
			msgReply = msgReply.await();
			OpenflowMessage reply = msgReply.reply();
			reply.validate();

			/* Create a request body to fill features for all the tables */
			MutableArray tableFeatureRequestBody = (MutableArray) MpBodyFactory.createRequestBody(ProtocolVersion.V_1_3,
					MultipartType.TABLE_FEATURES);

			// tableFeatureRequestBody.addTableFeatures(createICMPv4TableProperties());
			// tableFeatureRequestBody.addTableFeatures(createARPSourceTableProperties());
			// tableFeatureRequestBody.addTableFeatures(createARPDestinationTableProperties());
			// tableFeatureRequestBody.addTableFeatures(createICMPv6TableProperties());
			// tableFeatureRequestBody.addTableFeatures(createIPV6NDTargetTableProperties());
			// tableFeatureRequestBody.addTableFeatures(createIPV6FlowTableTableProperties());
			// tableFeatureRequestBody.addTableFeatures(createCustomMatchTableProperties());
			// tableFeatureRequestBody.addTableFeatures(createMixedTableProperties());

			tableFeatureRequestBody.addTableFeatures(hpeSDNTableARPBypass());
			tableFeatureRequestBody.addTableFeatures(hpeSDNTableTopologyLearning());
			tableFeatureRequestBody.addTableFeatures(hpeSDNTableVisibilityCopyTunnel());
			tableFeatureRequestBody.addTableFeatures(hpeSDNTableQuarantine());
			tableFeatureRequestBody.addTableFeatures(hpeSDNTableQoS());
			tableFeatureRequestBody.addTableFeatures(hpeSDNTableIPSaaSFirewallOne());
			tableFeatureRequestBody.addTableFeatures(hpeSDNTableIPSaaSFirewallTwo());
			tableFeatureRequestBody.addTableFeatures(hpeSDNTableHybrid());

			/* Create a new multipart request body */
			MultipartBody tableFeatureMPBody = MpBodyFactory.createRequestBody(ProtocolVersion.V_1_3,
					MultipartType.TABLE_FEATURES);

			tableFeatureMPBody = tableFeatureRequestBody;

			/* Create a multipart request to send the new pipeline */
			OfmMutableMultipartRequest tableFeatureMPRequest = (OfmMutableMultipartRequest) MessageFactory
					.create(ProtocolVersion.V_1_3, MessageType.MULTIPART_REQUEST, MultipartType.TABLE_FEATURES);

			tableFeatureMPRequest.body(tableFeatureMPBody);

			/* Send the new pipeline message and wait for a response */
			MessageFuture tblFtrReplyMsg = controller.send(tableFeatureMPRequest.toImmutable(), datapathId);

			tblFtrReplyMsg = tblFtrReplyMsg.await();
			OpenflowMessage reply2 = tblFtrReplyMsg.reply();
			reply2.validate();

			// cbsGenerateFlowMod();

		} catch (OpenflowException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}

		log.info("PipelineInfo Collected for DataPath: " + datapathId.toString());

	}

	public MBodyTableFeatures createICMPv4TableProperties() {

		/*
		 * Now let us create a Table Features Request message with a new
		 * pipeline and send it to the switch
		 */

		MBodyMutableTableFeatures tableFeatures = new MBodyMutableTableFeatures(ProtocolVersion.V_1_3);

		/* Set the table ID for the new table */
		TableId tblID = TableId.valueOf(0);
		tableFeatures.tableId(tblID);

		/* Set a name for the new table */
		tableFeatures.name("OpenFlow ICMPv4 Table");

		/* Set the metadata match & metadata write fields for the table */
		tableFeatures.metadataWrite(0x00);
		tableFeatures.metadataMatch(0x00);

		/* Set the size of the new table */
		tableFeatures.maxEntries(128);

		/* Set the instructions to be supported in the new table */
		Set<InstructionType> tableInstrTypes = new HashSet<InstructionType>();
		tableInstrTypes.add(InstructionType.APPLY_ACTIONS);
		tableInstrTypes.add(InstructionType.WRITE_ACTIONS);
		tableInstrTypes.add(InstructionType.GOTO_TABLE);
		tableInstrTypes.add(InstructionType.METER);

		tableFeatures.addProp(TableFeatureFactory.createInstrProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.INSTRUCTIONS, tableInstrTypes));

		/* Set the instructions to be supported for the table miss rule */
		Set<InstructionType> tableInstrMissTypes = new HashSet<InstructionType>();
		tableInstrMissTypes.add(InstructionType.APPLY_ACTIONS);
		tableInstrMissTypes.add(InstructionType.WRITE_ACTIONS);
		tableInstrMissTypes.add(InstructionType.GOTO_TABLE);
		tableInstrMissTypes.add(InstructionType.METER);

		tableFeatures.addProp(TableFeatureFactory.createInstrProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.INSTRUCTIONS_MISS, tableInstrMissTypes));

		/*
		 * Set the tables to which rules can be pointed to from the current
		 * table
		 */
		Set<TableId> tableNextTables = new HashSet<TableId>();
		tableNextTables.add(TableId.valueOf(1));
		tableNextTables.add(TableId.valueOf(2));
		tableNextTables.add(TableId.valueOf(3));
		tableNextTables.add(TableId.valueOf(4));
		tableNextTables.add(TableId.valueOf(5));
		tableNextTables.add(TableId.valueOf(6));
		tableNextTables.add(TableId.valueOf(7));
		tableFeatures.addProp(TableFeatureFactory.createNextTablesProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.NEXT_TABLES, tableNextTables));

		/*
		 * Set the tables to which the table miss rule can be point to from the
		 * current table
		 */
		Set<TableId> tableNextTablesMiss = new HashSet<TableId>();
		tableNextTablesMiss.add(TableId.valueOf(1));
		tableNextTablesMiss.add(TableId.valueOf(2));
		tableNextTablesMiss.add(TableId.valueOf(3));
		tableNextTablesMiss.add(TableId.valueOf(4));
		tableNextTablesMiss.add(TableId.valueOf(5));
		tableNextTablesMiss.add(TableId.valueOf(6));
		tableNextTablesMiss.add(TableId.valueOf(7));
		tableFeatures.addProp(TableFeatureFactory.createNextTablesProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.NEXT_TABLES_MISS, tableNextTablesMiss));

		/* Set the actions to be supported via WRITE instruction */
		Set<ActionType> tableWriteActions = new HashSet<ActionType>();
		tableWriteActions.add(ActionType.OUTPUT);
		tableWriteActions.add(ActionType.SET_FIELD);
		tableFeatures.addProp(TableFeatureFactory.createActionProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.WRITE_ACTIONS, tableWriteActions));

		/*
		 * Set the actions to be supported via WRITE instruction for a table
		 * miss rule
		 */
		Set<ActionType> tableWriteActionsMiss = new HashSet<ActionType>();
		tableWriteActionsMiss.add(ActionType.OUTPUT);
		tableWriteActionsMiss.add(ActionType.SET_FIELD);
		tableFeatures.addProp(TableFeatureFactory.createActionProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.WRITE_ACTIONS_MISS, tableWriteActionsMiss));

		/* Set the actions to be supported via APPLY instruction */
		Set<ActionType> tableApplyActions = new HashSet<ActionType>();
		tableApplyActions.add(ActionType.OUTPUT);
		tableApplyActions.add(ActionType.SET_FIELD);
		tableFeatures.addProp(TableFeatureFactory.createActionProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.APPLY_ACTIONS, tableApplyActions));

		/*
		 * Set the actions to be supported via APPLY instruction for a table
		 * miss rule
		 */
		Set<ActionType> tableApplyActionsMiss = new HashSet<ActionType>();
		tableApplyActionsMiss.add(ActionType.OUTPUT);
		tableApplyActionsMiss.add(ActionType.SET_FIELD);
		tableFeatures.addProp(TableFeatureFactory.createActionProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.APPLY_ACTIONS_MISS, tableApplyActionsMiss));

		/* Set the fields the table will match on */
		Map<OxmBasicFieldType, Boolean> tableMatchFields = new HashMap<OxmBasicFieldType, Boolean>();
		tableMatchFields.put(OxmBasicFieldType.ETH_TYPE, false);
		tableMatchFields.put(OxmBasicFieldType.IP_PROTO, false);
		tableMatchFields.put(OxmBasicFieldType.ICMPV4_TYPE, false);
		tableMatchFields.put(OxmBasicFieldType.ICMPV4_CODE, false);

		tableFeatures.addProp(
				TableFeatureFactory.createOxmProp(ProtocolVersion.V_1_3, TableFeaturePropType.MATCH, tableMatchFields));

		/* Set the match fields that can be wildcarded */
		Map<OxmBasicFieldType, Boolean> tableWildcards = new HashMap<OxmBasicFieldType, Boolean>();
		tableWildcards.put(OxmBasicFieldType.ETH_TYPE, false);
		tableWildcards.put(OxmBasicFieldType.IP_PROTO, false);
		tableWildcards.put(OxmBasicFieldType.ICMPV4_TYPE, false);
		tableWildcards.put(OxmBasicFieldType.ICMPV4_CODE, false);

		tableFeatures.addProp(TableFeatureFactory.createOxmProp(ProtocolVersion.V_1_3, TableFeaturePropType.WILDCARDS,
				tableWildcards));

		/* Set the fields that can be modified via a WRITE instruction */
		Map<OxmBasicFieldType, Boolean> tableWriteSetFields = new HashMap<OxmBasicFieldType, Boolean>();
		tableWriteSetFields.put(OxmBasicFieldType.VLAN_VID, false);
		tableWriteSetFields.put(OxmBasicFieldType.VLAN_PCP, false);
		tableWriteSetFields.put(OxmBasicFieldType.ETH_DST, false);
		tableWriteSetFields.put(OxmBasicFieldType.ETH_SRC, false);
		tableFeatures.addProp(TableFeatureFactory.createOxmProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.WRITE_SETFIELD, tableWriteSetFields));

		/*
		 * Set the fields that can be modified via a WRITE instruction for a
		 * miss rule
		 */
		Map<OxmBasicFieldType, Boolean> tableWriteSetFieldsMiss = new HashMap<OxmBasicFieldType, Boolean>();
		tableWriteSetFieldsMiss.put(OxmBasicFieldType.VLAN_VID, false);
		tableWriteSetFieldsMiss.put(OxmBasicFieldType.VLAN_PCP, false);
		tableWriteSetFieldsMiss.put(OxmBasicFieldType.ETH_DST, false);
		tableWriteSetFieldsMiss.put(OxmBasicFieldType.ETH_SRC, false);
		tableFeatures.addProp(TableFeatureFactory.createOxmProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.WRITE_SETFIELD_MISS, tableWriteSetFieldsMiss));

		/* Set the fields that can be modified via a APPLY instruction */
		Map<OxmBasicFieldType, Boolean> tableApplySetFields = new HashMap<OxmBasicFieldType, Boolean>();
		tableApplySetFields.put(OxmBasicFieldType.VLAN_VID, false);
		tableApplySetFields.put(OxmBasicFieldType.VLAN_PCP, false);
		tableApplySetFields.put(OxmBasicFieldType.ETH_DST, false);
		tableApplySetFields.put(OxmBasicFieldType.ETH_SRC, false);
		tableFeatures.addProp(TableFeatureFactory.createOxmProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.APPLY_SETFIELD, tableApplySetFields));

		/*
		 * Set the fields that can be modified via a APPLY instruction for a
		 * miss rule
		 */
		Map<OxmBasicFieldType, Boolean> tableApplySetFieldsMiss = new HashMap<OxmBasicFieldType, Boolean>();
		tableApplySetFieldsMiss.put(OxmBasicFieldType.VLAN_VID, false);
		tableApplySetFieldsMiss.put(OxmBasicFieldType.VLAN_PCP, false);
		tableApplySetFieldsMiss.put(OxmBasicFieldType.ETH_DST, false);
		tableApplySetFieldsMiss.put(OxmBasicFieldType.ETH_SRC, false);
		tableFeatures.addProp(TableFeatureFactory.createOxmProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.APPLY_SETFIELD_MISS, tableApplySetFieldsMiss));

		return (MBodyTableFeatures) tableFeatures.toImmutable();
	}

	public MBodyTableFeatures createARPSourceTableProperties() {

		/*
		 * Now let us create a Table Features Request message with a new
		 * pipeline and send it to the switch
		 */

		MBodyMutableTableFeatures tableFeatures = new MBodyMutableTableFeatures(ProtocolVersion.V_1_3);

		/* Set the table ID for the new table */
		TableId tblID = TableId.valueOf(1);
		tableFeatures.tableId(tblID);

		/* Set a name for the new table */
		tableFeatures.name("OpenFlow ARP Source Table");

		/* Set the metadata match & metadata write fields for the table */
		tableFeatures.metadataWrite(0x00);
		tableFeatures.metadataMatch(0x00);

		/* Set the size of the new table */
		tableFeatures.maxEntries(128);

		/* Set the instructions to be supported in the new table */
		Set<InstructionType> tableInstrTypes = new HashSet<InstructionType>();
		tableInstrTypes.add(InstructionType.APPLY_ACTIONS);
		tableInstrTypes.add(InstructionType.WRITE_ACTIONS);
		tableInstrTypes.add(InstructionType.GOTO_TABLE);
		tableInstrTypes.add(InstructionType.METER);

		tableFeatures.addProp(TableFeatureFactory.createInstrProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.INSTRUCTIONS, tableInstrTypes));

		/* Set the instructions to be supported for the table miss rule */
		Set<InstructionType> tableInstrMissTypes = new HashSet<InstructionType>();
		tableInstrMissTypes.add(InstructionType.APPLY_ACTIONS);
		tableInstrMissTypes.add(InstructionType.WRITE_ACTIONS);
		tableInstrMissTypes.add(InstructionType.GOTO_TABLE);
		tableInstrMissTypes.add(InstructionType.METER);

		tableFeatures.addProp(TableFeatureFactory.createInstrProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.INSTRUCTIONS_MISS, tableInstrMissTypes));

		/*
		 * Set the tables to which rules can be pointed to from the current
		 * table
		 */
		Set<TableId> tableNextTables = new HashSet<TableId>();
		tableNextTables.add(TableId.valueOf(2));
		tableNextTables.add(TableId.valueOf(3));
		tableNextTables.add(TableId.valueOf(4));
		tableNextTables.add(TableId.valueOf(5));
		tableNextTables.add(TableId.valueOf(6));
		tableNextTables.add(TableId.valueOf(7));
		tableFeatures.addProp(TableFeatureFactory.createNextTablesProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.NEXT_TABLES, tableNextTables));

		/*
		 * Set the tables to which the table miss rule can be point to from the
		 * current table
		 */
		Set<TableId> tableNextTablesMiss = new HashSet<TableId>();
		tableNextTablesMiss.add(TableId.valueOf(2));
		tableNextTablesMiss.add(TableId.valueOf(3));
		tableNextTablesMiss.add(TableId.valueOf(4));
		tableNextTablesMiss.add(TableId.valueOf(5));
		tableNextTablesMiss.add(TableId.valueOf(6));
		tableNextTablesMiss.add(TableId.valueOf(7));
		tableFeatures.addProp(TableFeatureFactory.createNextTablesProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.NEXT_TABLES_MISS, tableNextTablesMiss));

		/* Set the actions to be supported via WRITE instruction */
		Set<ActionType> tableWriteActions = new HashSet<ActionType>();
		tableWriteActions.add(ActionType.OUTPUT);
		tableWriteActions.add(ActionType.SET_FIELD);
		tableFeatures.addProp(TableFeatureFactory.createActionProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.WRITE_ACTIONS, tableWriteActions));

		/*
		 * Set the actions to be supported via WRITE instruction for a table
		 * miss rule
		 */
		Set<ActionType> tableWriteActionsMiss = new HashSet<ActionType>();
		tableWriteActionsMiss.add(ActionType.OUTPUT);
		tableWriteActionsMiss.add(ActionType.SET_FIELD);
		tableFeatures.addProp(TableFeatureFactory.createActionProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.WRITE_ACTIONS_MISS, tableWriteActionsMiss));

		/* Set the actions to be supported via APPLY instruction */
		Set<ActionType> tableApplyActions = new HashSet<ActionType>();
		tableApplyActions.add(ActionType.OUTPUT);
		tableApplyActions.add(ActionType.SET_FIELD);
		tableFeatures.addProp(TableFeatureFactory.createActionProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.APPLY_ACTIONS, tableApplyActions));

		/*
		 * Set the actions to be supported via APPLY instruction for a table
		 * miss rule
		 */
		Set<ActionType> tableApplyActionsMiss = new HashSet<ActionType>();
		tableApplyActionsMiss.add(ActionType.OUTPUT);
		tableApplyActionsMiss.add(ActionType.SET_FIELD);
		tableFeatures.addProp(TableFeatureFactory.createActionProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.APPLY_ACTIONS_MISS, tableApplyActionsMiss));

		/* Set the fields the table will match on */
		Map<OxmBasicFieldType, Boolean> tableMatchFields = new HashMap<OxmBasicFieldType, Boolean>();
		tableMatchFields.put(OxmBasicFieldType.ETH_TYPE, false);
		tableMatchFields.put(OxmBasicFieldType.ARP_OP, false);
		tableMatchFields.put(OxmBasicFieldType.ARP_SHA, true);
		tableMatchFields.put(OxmBasicFieldType.ARP_SPA, true);
		tableFeatures.addProp(
				TableFeatureFactory.createOxmProp(ProtocolVersion.V_1_3, TableFeaturePropType.MATCH, tableMatchFields));

		/* Set the match fields that can be wildcarded */
		Map<OxmBasicFieldType, Boolean> tableWildcards = new HashMap<OxmBasicFieldType, Boolean>();
		tableWildcards.put(OxmBasicFieldType.ETH_TYPE, false);
		tableWildcards.put(OxmBasicFieldType.ARP_OP, false);
		tableWildcards.put(OxmBasicFieldType.ARP_SHA, false);
		tableWildcards.put(OxmBasicFieldType.ARP_SPA, false);
		tableFeatures.addProp(TableFeatureFactory.createOxmProp(ProtocolVersion.V_1_3, TableFeaturePropType.WILDCARDS,
				tableWildcards));

		/* Set the fields that can be modified via a WRITE instruction */
		Map<OxmBasicFieldType, Boolean> tableWriteSetFields = new HashMap<OxmBasicFieldType, Boolean>();
		tableWriteSetFields.put(OxmBasicFieldType.VLAN_VID, false);
		tableWriteSetFields.put(OxmBasicFieldType.VLAN_PCP, false);
		tableWriteSetFields.put(OxmBasicFieldType.ETH_DST, false);
		tableWriteSetFields.put(OxmBasicFieldType.ETH_SRC, false);
		tableFeatures.addProp(TableFeatureFactory.createOxmProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.WRITE_SETFIELD, tableWriteSetFields));

		/*
		 * Set the fields that can be modified via a WRITE instruction for a
		 * miss rule
		 */
		Map<OxmBasicFieldType, Boolean> tableWriteSetFieldsMiss = new HashMap<OxmBasicFieldType, Boolean>();
		tableWriteSetFieldsMiss.put(OxmBasicFieldType.VLAN_VID, false);
		tableWriteSetFieldsMiss.put(OxmBasicFieldType.VLAN_PCP, false);
		tableWriteSetFieldsMiss.put(OxmBasicFieldType.ETH_DST, false);
		tableWriteSetFieldsMiss.put(OxmBasicFieldType.ETH_SRC, false);
		tableFeatures.addProp(TableFeatureFactory.createOxmProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.WRITE_SETFIELD_MISS, tableWriteSetFieldsMiss));

		/* Set the fields that can be modified via a APPLY instruction */
		Map<OxmBasicFieldType, Boolean> tableApplySetFields = new HashMap<OxmBasicFieldType, Boolean>();
		tableApplySetFields.put(OxmBasicFieldType.VLAN_VID, false);
		tableApplySetFields.put(OxmBasicFieldType.VLAN_PCP, false);
		tableApplySetFields.put(OxmBasicFieldType.ETH_DST, false);
		tableApplySetFields.put(OxmBasicFieldType.ETH_SRC, false);
		tableFeatures.addProp(TableFeatureFactory.createOxmProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.APPLY_SETFIELD, tableApplySetFields));

		/*
		 * Set the fields that can be modified via a APPLY instruction for a
		 * miss rule
		 */
		Map<OxmBasicFieldType, Boolean> tableApplySetFieldsMiss = new HashMap<OxmBasicFieldType, Boolean>();
		tableApplySetFieldsMiss.put(OxmBasicFieldType.VLAN_VID, false);
		tableApplySetFieldsMiss.put(OxmBasicFieldType.VLAN_PCP, false);
		tableApplySetFieldsMiss.put(OxmBasicFieldType.ETH_DST, false);
		tableApplySetFieldsMiss.put(OxmBasicFieldType.ETH_SRC, false);
		tableFeatures.addProp(TableFeatureFactory.createOxmProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.APPLY_SETFIELD_MISS, tableApplySetFieldsMiss));

		return (MBodyTableFeatures) tableFeatures.toImmutable();
	}

	public MBodyTableFeatures createARPDestinationTableProperties() {

		/*
		 * Now let us create a Table Features Request message with a new
		 * pipeline and send it to the switch
		 */

		MBodyMutableTableFeatures tableFeatures = new MBodyMutableTableFeatures(ProtocolVersion.V_1_3);

		/* Set the table ID for the new table */
		TableId tblID = TableId.valueOf(2);
		tableFeatures.tableId(tblID);

		/* Set a name for the new table */
		tableFeatures.name("OpenFlow ARP Destination Table");

		/* Set the metadata match & metadata write fields for the table */
		tableFeatures.metadataWrite(0x00);
		tableFeatures.metadataMatch(0x00);

		/* Set the size of the new table */
		tableFeatures.maxEntries(128);

		/* Set the instructions to be supported in the new table */
		Set<InstructionType> tableInstrTypes = new HashSet<InstructionType>();
		tableInstrTypes.add(InstructionType.APPLY_ACTIONS);
		tableInstrTypes.add(InstructionType.WRITE_ACTIONS);
		tableInstrTypes.add(InstructionType.GOTO_TABLE);
		tableInstrTypes.add(InstructionType.METER);

		tableFeatures.addProp(TableFeatureFactory.createInstrProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.INSTRUCTIONS, tableInstrTypes));

		/* Set the instructions to be supported for the table miss rule */
		Set<InstructionType> tableInstrMissTypes = new HashSet<InstructionType>();
		tableInstrMissTypes.add(InstructionType.APPLY_ACTIONS);
		tableInstrMissTypes.add(InstructionType.WRITE_ACTIONS);
		tableInstrMissTypes.add(InstructionType.GOTO_TABLE);
		tableInstrMissTypes.add(InstructionType.METER);

		tableFeatures.addProp(TableFeatureFactory.createInstrProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.INSTRUCTIONS_MISS, tableInstrMissTypes));

		/*
		 * Set the tables to which rules can be pointed to from the current
		 * table
		 */
		Set<TableId> tableNextTables = new HashSet<TableId>();
		tableNextTables.add(TableId.valueOf(3));
		tableNextTables.add(TableId.valueOf(4));
		tableNextTables.add(TableId.valueOf(5));
		tableNextTables.add(TableId.valueOf(6));
		tableNextTables.add(TableId.valueOf(7));
		tableFeatures.addProp(TableFeatureFactory.createNextTablesProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.NEXT_TABLES, tableNextTables));

		/*
		 * Set the tables to which the table miss rule can be point to from the
		 * current table
		 */
		Set<TableId> tableNextTablesMiss = new HashSet<TableId>();
		tableNextTablesMiss.add(TableId.valueOf(3));
		tableNextTablesMiss.add(TableId.valueOf(4));
		tableNextTablesMiss.add(TableId.valueOf(5));
		tableNextTablesMiss.add(TableId.valueOf(6));
		tableNextTablesMiss.add(TableId.valueOf(7));
		tableFeatures.addProp(TableFeatureFactory.createNextTablesProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.NEXT_TABLES_MISS, tableNextTablesMiss));

		/* Set the actions to be supported via WRITE instruction */
		Set<ActionType> tableWriteActions = new HashSet<ActionType>();
		tableWriteActions.add(ActionType.OUTPUT);
		tableWriteActions.add(ActionType.SET_FIELD);
		tableFeatures.addProp(TableFeatureFactory.createActionProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.WRITE_ACTIONS, tableWriteActions));

		/*
		 * Set the actions to be supported via WRITE instruction for a table
		 * miss rule
		 */
		Set<ActionType> tableWriteActionsMiss = new HashSet<ActionType>();
		tableWriteActionsMiss.add(ActionType.OUTPUT);
		tableWriteActionsMiss.add(ActionType.SET_FIELD);
		tableFeatures.addProp(TableFeatureFactory.createActionProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.WRITE_ACTIONS_MISS, tableWriteActionsMiss));

		/* Set the actions to be supported via APPLY instruction */
		Set<ActionType> tableApplyActions = new HashSet<ActionType>();
		tableApplyActions.add(ActionType.OUTPUT);
		tableApplyActions.add(ActionType.SET_FIELD);
		tableFeatures.addProp(TableFeatureFactory.createActionProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.APPLY_ACTIONS, tableApplyActions));

		/*
		 * Set the actions to be supported via APPLY instruction for a table
		 * miss rule
		 */
		Set<ActionType> tableApplyActionsMiss = new HashSet<ActionType>();
		tableApplyActionsMiss.add(ActionType.OUTPUT);
		tableApplyActionsMiss.add(ActionType.SET_FIELD);
		tableFeatures.addProp(TableFeatureFactory.createActionProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.APPLY_ACTIONS_MISS, tableApplyActionsMiss));

		/* Set the fields the table will match on */
		Map<OxmBasicFieldType, Boolean> tableMatchFields = new HashMap<OxmBasicFieldType, Boolean>();
		tableMatchFields.put(OxmBasicFieldType.ETH_TYPE, false);
		tableMatchFields.put(OxmBasicFieldType.ARP_OP, false);
		tableMatchFields.put(OxmBasicFieldType.ARP_THA, true);
		tableMatchFields.put(OxmBasicFieldType.ARP_TPA, true);
		tableFeatures.addProp(
				TableFeatureFactory.createOxmProp(ProtocolVersion.V_1_3, TableFeaturePropType.MATCH, tableMatchFields));

		/* Set the match fields that can be wildcarded */
		Map<OxmBasicFieldType, Boolean> tableWildcards = new HashMap<OxmBasicFieldType, Boolean>();
		tableWildcards.put(OxmBasicFieldType.ETH_TYPE, false);
		tableWildcards.put(OxmBasicFieldType.ARP_OP, false);
		tableWildcards.put(OxmBasicFieldType.ARP_THA, false);
		tableWildcards.put(OxmBasicFieldType.ARP_TPA, false);
		tableFeatures.addProp(TableFeatureFactory.createOxmProp(ProtocolVersion.V_1_3, TableFeaturePropType.WILDCARDS,
				tableWildcards));

		/* Set the fields that can be modified via a WRITE instruction */
		Map<OxmBasicFieldType, Boolean> tableWriteSetFields = new HashMap<OxmBasicFieldType, Boolean>();
		tableWriteSetFields.put(OxmBasicFieldType.VLAN_VID, false);
		tableWriteSetFields.put(OxmBasicFieldType.VLAN_PCP, false);
		tableWriteSetFields.put(OxmBasicFieldType.ETH_DST, false);
		tableWriteSetFields.put(OxmBasicFieldType.ETH_SRC, false);
		tableFeatures.addProp(TableFeatureFactory.createOxmProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.WRITE_SETFIELD, tableWriteSetFields));

		/*
		 * Set the fields that can be modified via a WRITE instruction for a
		 * miss rule
		 */
		Map<OxmBasicFieldType, Boolean> tableWriteSetFieldsMiss = new HashMap<OxmBasicFieldType, Boolean>();
		tableWriteSetFieldsMiss.put(OxmBasicFieldType.VLAN_VID, false);
		tableWriteSetFieldsMiss.put(OxmBasicFieldType.VLAN_PCP, false);
		tableWriteSetFieldsMiss.put(OxmBasicFieldType.ETH_DST, false);
		tableWriteSetFieldsMiss.put(OxmBasicFieldType.ETH_SRC, false);
		tableFeatures.addProp(TableFeatureFactory.createOxmProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.WRITE_SETFIELD_MISS, tableWriteSetFieldsMiss));

		/* Set the fields that can be modified via a APPLY instruction */
		Map<OxmBasicFieldType, Boolean> tableApplySetFields = new HashMap<OxmBasicFieldType, Boolean>();
		tableApplySetFields.put(OxmBasicFieldType.VLAN_VID, false);
		tableApplySetFields.put(OxmBasicFieldType.VLAN_PCP, false);
		tableApplySetFields.put(OxmBasicFieldType.ETH_DST, false);
		tableApplySetFields.put(OxmBasicFieldType.ETH_SRC, false);
		tableFeatures.addProp(TableFeatureFactory.createOxmProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.APPLY_SETFIELD, tableApplySetFields));

		/*
		 * Set the fields that can be modified via a APPLY instruction for a
		 * miss rule
		 */
		Map<OxmBasicFieldType, Boolean> tableApplySetFieldsMiss = new HashMap<OxmBasicFieldType, Boolean>();
		tableApplySetFieldsMiss.put(OxmBasicFieldType.VLAN_VID, false);
		tableApplySetFieldsMiss.put(OxmBasicFieldType.VLAN_PCP, false);
		tableApplySetFieldsMiss.put(OxmBasicFieldType.ETH_DST, false);
		tableApplySetFieldsMiss.put(OxmBasicFieldType.ETH_SRC, false);
		tableFeatures.addProp(TableFeatureFactory.createOxmProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.APPLY_SETFIELD_MISS, tableApplySetFieldsMiss));

		return (MBodyTableFeatures) tableFeatures.toImmutable();
	}

	public MBodyTableFeatures createICMPv6TableProperties() {

		/*
		 * Now let us create a Table Features Request message with a new
		 * pipeline and send it to the switch
		 */

		MBodyMutableTableFeatures tableFeatures = new MBodyMutableTableFeatures(ProtocolVersion.V_1_3);

		/* Set the table ID for the new table */
		TableId tblID = TableId.valueOf(3);
		tableFeatures.tableId(tblID);

		/* Set a name for the new table */
		tableFeatures.name("OpenFlow ICMPv6 Table");

		/* Set the metadata match & metadata write fields for the table */
		tableFeatures.metadataWrite(0x00);
		tableFeatures.metadataMatch(0x00);

		/* Set the size of the new table */
		tableFeatures.maxEntries(128);

		/* Set the instructions to be supported in the new table */
		Set<InstructionType> tableInstrTypes = new HashSet<InstructionType>();
		tableInstrTypes.add(InstructionType.APPLY_ACTIONS);
		tableInstrTypes.add(InstructionType.WRITE_ACTIONS);
		tableInstrTypes.add(InstructionType.GOTO_TABLE);
		tableInstrTypes.add(InstructionType.METER);

		tableFeatures.addProp(TableFeatureFactory.createInstrProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.INSTRUCTIONS, tableInstrTypes));

		/* Set the instructions to be supported for the table miss rule */
		Set<InstructionType> tableInstrMissTypes = new HashSet<InstructionType>();
		tableInstrMissTypes.add(InstructionType.APPLY_ACTIONS);
		tableInstrMissTypes.add(InstructionType.WRITE_ACTIONS);
		tableInstrMissTypes.add(InstructionType.GOTO_TABLE);
		tableInstrMissTypes.add(InstructionType.METER);

		tableFeatures.addProp(TableFeatureFactory.createInstrProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.INSTRUCTIONS_MISS, tableInstrMissTypes));

		/*
		 * Set the tables to which rules can be pointed to from the current
		 * table
		 */
		Set<TableId> tableNextTables = new HashSet<TableId>();
		tableNextTables.add(TableId.valueOf(4));
		tableNextTables.add(TableId.valueOf(5));
		tableNextTables.add(TableId.valueOf(6));
		tableNextTables.add(TableId.valueOf(7));
		tableFeatures.addProp(TableFeatureFactory.createNextTablesProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.NEXT_TABLES, tableNextTables));

		/*
		 * Set the tables to which the table miss rule can be point to from the
		 * current table
		 */
		Set<TableId> tableNextTablesMiss = new HashSet<TableId>();
		tableNextTablesMiss.add(TableId.valueOf(4));
		tableNextTablesMiss.add(TableId.valueOf(5));
		tableNextTablesMiss.add(TableId.valueOf(6));
		tableNextTablesMiss.add(TableId.valueOf(7));
		tableFeatures.addProp(TableFeatureFactory.createNextTablesProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.NEXT_TABLES_MISS, tableNextTablesMiss));

		/* Set the actions to be supported via WRITE instruction */
		Set<ActionType> tableWriteActions = new HashSet<ActionType>();
		tableWriteActions.add(ActionType.OUTPUT);
		tableWriteActions.add(ActionType.SET_FIELD);
		tableFeatures.addProp(TableFeatureFactory.createActionProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.WRITE_ACTIONS, tableWriteActions));

		/*
		 * Set the actions to be supported via WRITE instruction for a table
		 * miss rule
		 */
		Set<ActionType> tableWriteActionsMiss = new HashSet<ActionType>();
		tableWriteActionsMiss.add(ActionType.OUTPUT);
		tableWriteActionsMiss.add(ActionType.SET_FIELD);
		tableFeatures.addProp(TableFeatureFactory.createActionProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.WRITE_ACTIONS_MISS, tableWriteActionsMiss));

		/* Set the actions to be supported via APPLY instruction */
		Set<ActionType> tableApplyActions = new HashSet<ActionType>();
		tableApplyActions.add(ActionType.OUTPUT);
		tableApplyActions.add(ActionType.SET_FIELD);
		tableFeatures.addProp(TableFeatureFactory.createActionProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.APPLY_ACTIONS, tableApplyActions));

		/*
		 * Set the actions to be supported via APPLY instruction for a table
		 * miss rule
		 */
		Set<ActionType> tableApplyActionsMiss = new HashSet<ActionType>();
		tableApplyActionsMiss.add(ActionType.OUTPUT);
		tableApplyActionsMiss.add(ActionType.SET_FIELD);
		tableFeatures.addProp(TableFeatureFactory.createActionProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.APPLY_ACTIONS_MISS, tableApplyActionsMiss));

		/* Set the fields the table will match on */
		Map<OxmBasicFieldType, Boolean> tableMatchFields = new HashMap<OxmBasicFieldType, Boolean>();
		tableMatchFields.put(OxmBasicFieldType.ETH_TYPE, false);
		tableMatchFields.put(OxmBasicFieldType.IP_PROTO, false);
		tableMatchFields.put(OxmBasicFieldType.ICMPV6_TYPE, false);
		tableMatchFields.put(OxmBasicFieldType.ICMPV6_CODE, false);
		tableFeatures.addProp(
				TableFeatureFactory.createOxmProp(ProtocolVersion.V_1_3, TableFeaturePropType.MATCH, tableMatchFields));

		/* Set the match fields that can be wildcarded */
		Map<OxmBasicFieldType, Boolean> tableWildcards = new HashMap<OxmBasicFieldType, Boolean>();
		tableWildcards.put(OxmBasicFieldType.ETH_TYPE, false);
		tableWildcards.put(OxmBasicFieldType.IP_PROTO, false);
		tableWildcards.put(OxmBasicFieldType.ICMPV6_TYPE, false);
		tableWildcards.put(OxmBasicFieldType.ICMPV6_CODE, false);
		tableFeatures.addProp(TableFeatureFactory.createOxmProp(ProtocolVersion.V_1_3, TableFeaturePropType.WILDCARDS,
				tableWildcards));

		/* Set the fields that can be modified via a WRITE instruction */
		Map<OxmBasicFieldType, Boolean> tableWriteSetFields = new HashMap<OxmBasicFieldType, Boolean>();
		tableWriteSetFields.put(OxmBasicFieldType.VLAN_VID, false);
		tableWriteSetFields.put(OxmBasicFieldType.VLAN_PCP, false);
		tableWriteSetFields.put(OxmBasicFieldType.ETH_DST, false);
		tableWriteSetFields.put(OxmBasicFieldType.ETH_SRC, false);
		tableFeatures.addProp(TableFeatureFactory.createOxmProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.WRITE_SETFIELD, tableWriteSetFields));

		/*
		 * Set the fields that can be modified via a WRITE instruction for a
		 * miss rule
		 */
		Map<OxmBasicFieldType, Boolean> tableWriteSetFieldsMiss = new HashMap<OxmBasicFieldType, Boolean>();
		tableWriteSetFieldsMiss.put(OxmBasicFieldType.VLAN_VID, false);
		tableWriteSetFieldsMiss.put(OxmBasicFieldType.VLAN_PCP, false);
		tableWriteSetFieldsMiss.put(OxmBasicFieldType.ETH_DST, false);
		tableWriteSetFieldsMiss.put(OxmBasicFieldType.ETH_SRC, false);
		tableFeatures.addProp(TableFeatureFactory.createOxmProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.WRITE_SETFIELD_MISS, tableWriteSetFieldsMiss));

		/* Set the fields that can be modified via a APPLY instruction */
		Map<OxmBasicFieldType, Boolean> tableApplySetFields = new HashMap<OxmBasicFieldType, Boolean>();
		tableApplySetFields.put(OxmBasicFieldType.VLAN_VID, false);
		tableApplySetFields.put(OxmBasicFieldType.VLAN_PCP, false);
		tableApplySetFields.put(OxmBasicFieldType.ETH_DST, false);
		tableApplySetFields.put(OxmBasicFieldType.ETH_SRC, false);
		tableFeatures.addProp(TableFeatureFactory.createOxmProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.APPLY_SETFIELD, tableApplySetFields));

		/*
		 * Set the fields that can be modified via a APPLY instruction for a
		 * miss rule
		 */
		Map<OxmBasicFieldType, Boolean> tableApplySetFieldsMiss = new HashMap<OxmBasicFieldType, Boolean>();
		tableApplySetFieldsMiss.put(OxmBasicFieldType.VLAN_VID, false);
		tableApplySetFieldsMiss.put(OxmBasicFieldType.VLAN_PCP, false);
		tableApplySetFieldsMiss.put(OxmBasicFieldType.ETH_DST, false);
		tableApplySetFieldsMiss.put(OxmBasicFieldType.ETH_SRC, false);
		tableFeatures.addProp(TableFeatureFactory.createOxmProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.APPLY_SETFIELD_MISS, tableApplySetFieldsMiss));

		return (MBodyTableFeatures) tableFeatures.toImmutable();
	}

	public MBodyTableFeatures createIPV6NDTargetTableProperties() {

		/*
		 * Now let us create a Table Features Request message with a new
		 * pipeline and send it to the switch
		 */

		MBodyMutableTableFeatures tableFeatures = new MBodyMutableTableFeatures(ProtocolVersion.V_1_3);

		/* Set the table ID for the new table */
		TableId tblID = TableId.valueOf(4);
		tableFeatures.tableId(tblID);

		/* Set a name for the new table */
		tableFeatures.name("OpenFlow IPv6 ND Target Table");

		/* Set the metadata match & metadata write fields for the table */
		tableFeatures.metadataWrite(0x00);
		tableFeatures.metadataMatch(0x00);

		/* Set the size of the new table */
		tableFeatures.maxEntries(128);

		/* Set the instructions to be supported in the new table */
		Set<InstructionType> tableInstrTypes = new HashSet<InstructionType>();
		tableInstrTypes.add(InstructionType.APPLY_ACTIONS);
		tableInstrTypes.add(InstructionType.WRITE_ACTIONS);
		tableInstrTypes.add(InstructionType.GOTO_TABLE);
		tableInstrTypes.add(InstructionType.METER);

		tableFeatures.addProp(TableFeatureFactory.createInstrProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.INSTRUCTIONS, tableInstrTypes));

		/* Set the instructions to be supported for the table miss rule */
		Set<InstructionType> tableInstrMissTypes = new HashSet<InstructionType>();
		tableInstrMissTypes.add(InstructionType.APPLY_ACTIONS);
		tableInstrMissTypes.add(InstructionType.WRITE_ACTIONS);
		tableInstrMissTypes.add(InstructionType.GOTO_TABLE);
		tableInstrMissTypes.add(InstructionType.METER);

		tableFeatures.addProp(TableFeatureFactory.createInstrProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.INSTRUCTIONS_MISS, tableInstrMissTypes));

		/*
		 * Set the tables to which rules can be pointed to from the current
		 * table
		 */
		Set<TableId> tableNextTables = new HashSet<TableId>();
		tableNextTables.add(TableId.valueOf(5));
		tableNextTables.add(TableId.valueOf(6));
		tableNextTables.add(TableId.valueOf(7));
		tableFeatures.addProp(TableFeatureFactory.createNextTablesProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.NEXT_TABLES, tableNextTables));

		/*
		 * Set the tables to which the table miss rule can be point to from the
		 * current table
		 */
		Set<TableId> tableNextTablesMiss = new HashSet<TableId>();
		tableNextTablesMiss.add(TableId.valueOf(5));
		tableNextTablesMiss.add(TableId.valueOf(6));
		tableNextTablesMiss.add(TableId.valueOf(7));
		tableFeatures.addProp(TableFeatureFactory.createNextTablesProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.NEXT_TABLES_MISS, tableNextTablesMiss));

		/* Set the actions to be supported via WRITE instruction */
		Set<ActionType> tableWriteActions = new HashSet<ActionType>();
		tableWriteActions.add(ActionType.OUTPUT);
		tableWriteActions.add(ActionType.SET_FIELD);
		tableFeatures.addProp(TableFeatureFactory.createActionProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.WRITE_ACTIONS, tableWriteActions));

		/*
		 * Set the actions to be supported via WRITE instruction for a table
		 * miss rule
		 */
		Set<ActionType> tableWriteActionsMiss = new HashSet<ActionType>();
		tableWriteActionsMiss.add(ActionType.OUTPUT);
		tableWriteActionsMiss.add(ActionType.SET_FIELD);
		tableFeatures.addProp(TableFeatureFactory.createActionProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.WRITE_ACTIONS_MISS, tableWriteActionsMiss));

		/* Set the actions to be supported via APPLY instruction */
		Set<ActionType> tableApplyActions = new HashSet<ActionType>();
		tableApplyActions.add(ActionType.OUTPUT);
		tableApplyActions.add(ActionType.SET_FIELD);
		tableFeatures.addProp(TableFeatureFactory.createActionProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.APPLY_ACTIONS, tableApplyActions));

		/*
		 * Set the actions to be supported via APPLY instruction for a table
		 * miss rule
		 */
		Set<ActionType> tableApplyActionsMiss = new HashSet<ActionType>();
		tableApplyActionsMiss.add(ActionType.OUTPUT);
		tableApplyActionsMiss.add(ActionType.SET_FIELD);
		tableFeatures.addProp(TableFeatureFactory.createActionProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.APPLY_ACTIONS_MISS, tableApplyActionsMiss));

		/* Set the fields the table will match on */
		Map<OxmBasicFieldType, Boolean> tableMatchFields = new HashMap<OxmBasicFieldType, Boolean>();
		tableMatchFields.put(OxmBasicFieldType.ETH_TYPE, false);
		tableMatchFields.put(OxmBasicFieldType.IP_PROTO, false);
		tableMatchFields.put(OxmBasicFieldType.ICMPV6_TYPE, false);
		tableMatchFields.put(OxmBasicFieldType.IPV6_ND_TARGET, true);
		tableFeatures.addProp(
				TableFeatureFactory.createOxmProp(ProtocolVersion.V_1_3, TableFeaturePropType.MATCH, tableMatchFields));

		/* Set the match fields that can be wildcarded */
		Map<OxmBasicFieldType, Boolean> tableWildcards = new HashMap<OxmBasicFieldType, Boolean>();
		tableWildcards.put(OxmBasicFieldType.ETH_TYPE, false);
		tableWildcards.put(OxmBasicFieldType.IP_PROTO, false);
		tableWildcards.put(OxmBasicFieldType.ICMPV6_TYPE, false);
		tableMatchFields.put(OxmBasicFieldType.IPV6_ND_TARGET, false);
		tableFeatures.addProp(TableFeatureFactory.createOxmProp(ProtocolVersion.V_1_3, TableFeaturePropType.WILDCARDS,
				tableWildcards));

		/* Set the fields that can be modified via a WRITE instruction */
		Map<OxmBasicFieldType, Boolean> tableWriteSetFields = new HashMap<OxmBasicFieldType, Boolean>();
		tableWriteSetFields.put(OxmBasicFieldType.VLAN_VID, false);
		tableWriteSetFields.put(OxmBasicFieldType.VLAN_PCP, false);
		tableWriteSetFields.put(OxmBasicFieldType.ETH_DST, false);
		tableWriteSetFields.put(OxmBasicFieldType.ETH_SRC, false);
		tableFeatures.addProp(TableFeatureFactory.createOxmProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.WRITE_SETFIELD, tableWriteSetFields));

		/*
		 * Set the fields that can be modified via a WRITE instruction for a
		 * miss rule
		 */
		Map<OxmBasicFieldType, Boolean> tableWriteSetFieldsMiss = new HashMap<OxmBasicFieldType, Boolean>();
		tableWriteSetFieldsMiss.put(OxmBasicFieldType.VLAN_VID, false);
		tableWriteSetFieldsMiss.put(OxmBasicFieldType.VLAN_PCP, false);
		tableWriteSetFieldsMiss.put(OxmBasicFieldType.ETH_DST, false);
		tableWriteSetFieldsMiss.put(OxmBasicFieldType.ETH_SRC, false);
		tableFeatures.addProp(TableFeatureFactory.createOxmProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.WRITE_SETFIELD_MISS, tableWriteSetFieldsMiss));

		/* Set the fields that can be modified via a APPLY instruction */
		Map<OxmBasicFieldType, Boolean> tableApplySetFields = new HashMap<OxmBasicFieldType, Boolean>();
		tableApplySetFields.put(OxmBasicFieldType.VLAN_VID, false);
		tableApplySetFields.put(OxmBasicFieldType.VLAN_PCP, false);
		tableApplySetFields.put(OxmBasicFieldType.ETH_DST, false);
		tableApplySetFields.put(OxmBasicFieldType.ETH_SRC, false);
		tableFeatures.addProp(TableFeatureFactory.createOxmProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.APPLY_SETFIELD, tableApplySetFields));

		/*
		 * Set the fields that can be modified via a APPLY instruction for a
		 * miss rule
		 */
		Map<OxmBasicFieldType, Boolean> tableApplySetFieldsMiss = new HashMap<OxmBasicFieldType, Boolean>();
		tableApplySetFieldsMiss.put(OxmBasicFieldType.VLAN_VID, false);
		tableApplySetFieldsMiss.put(OxmBasicFieldType.VLAN_PCP, false);
		tableApplySetFieldsMiss.put(OxmBasicFieldType.ETH_DST, false);
		tableApplySetFieldsMiss.put(OxmBasicFieldType.ETH_SRC, false);
		tableFeatures.addProp(TableFeatureFactory.createOxmProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.APPLY_SETFIELD_MISS, tableApplySetFieldsMiss));

		return (MBodyTableFeatures) tableFeatures.toImmutable();
	}

	public MBodyTableFeatures createIPV6FlowTableTableProperties() {

		/*
		 * Now let us create a Table Features Request message with a new
		 * pipeline and send it to the switch
		 */

		MBodyMutableTableFeatures tableFeatures = new MBodyMutableTableFeatures(ProtocolVersion.V_1_3);

		/* Set the table ID for the new table */
		TableId tblID = TableId.valueOf(5);
		tableFeatures.tableId(tblID);

		/* Set a name for the new table */
		tableFeatures.name("OpenFlow IPv6 Flow Label");

		/* Set the metadata match & metadata write fields for the table */
		tableFeatures.metadataWrite(0x00);
		tableFeatures.metadataMatch(0x00);

		/* Set the size of the new table */
		tableFeatures.maxEntries(128);

		/* Set the instructions to be supported in the new table */
		Set<InstructionType> tableInstrTypes = new HashSet<InstructionType>();
		tableInstrTypes.add(InstructionType.APPLY_ACTIONS);
		tableInstrTypes.add(InstructionType.WRITE_ACTIONS);
		tableInstrTypes.add(InstructionType.GOTO_TABLE);
		tableInstrTypes.add(InstructionType.METER);

		tableFeatures.addProp(TableFeatureFactory.createInstrProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.INSTRUCTIONS, tableInstrTypes));

		/* Set the instructions to be supported for the table miss rule */
		Set<InstructionType> tableInstrMissTypes = new HashSet<InstructionType>();
		tableInstrMissTypes.add(InstructionType.APPLY_ACTIONS);
		tableInstrMissTypes.add(InstructionType.WRITE_ACTIONS);
		tableInstrMissTypes.add(InstructionType.GOTO_TABLE);
		tableInstrMissTypes.add(InstructionType.METER);

		tableFeatures.addProp(TableFeatureFactory.createInstrProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.INSTRUCTIONS_MISS, tableInstrMissTypes));

		/*
		 * Set the tables to which rules can be pointed to from the current
		 * table
		 */
		Set<TableId> tableNextTables = new HashSet<TableId>();
		tableNextTables.add(TableId.valueOf(6));
		tableNextTables.add(TableId.valueOf(7));
		tableFeatures.addProp(TableFeatureFactory.createNextTablesProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.NEXT_TABLES, tableNextTables));

		/*
		 * Set the tables to which the table miss rule can be point to from the
		 * current table
		 */
		Set<TableId> tableNextTablesMiss = new HashSet<TableId>();
		tableNextTables.add(TableId.valueOf(6));
		tableNextTables.add(TableId.valueOf(7));
		tableFeatures.addProp(TableFeatureFactory.createNextTablesProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.NEXT_TABLES_MISS, tableNextTablesMiss));

		/* Set the actions to be supported via WRITE instruction */
		Set<ActionType> tableWriteActions = new HashSet<ActionType>();
		tableWriteActions.add(ActionType.OUTPUT);
		tableWriteActions.add(ActionType.SET_FIELD);
		tableFeatures.addProp(TableFeatureFactory.createActionProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.WRITE_ACTIONS, tableWriteActions));

		/*
		 * Set the actions to be supported via WRITE instruction for a table
		 * miss rule
		 */
		Set<ActionType> tableWriteActionsMiss = new HashSet<ActionType>();
		tableWriteActionsMiss.add(ActionType.OUTPUT);
		tableWriteActionsMiss.add(ActionType.SET_FIELD);
		tableFeatures.addProp(TableFeatureFactory.createActionProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.WRITE_ACTIONS_MISS, tableWriteActionsMiss));

		/* Set the actions to be supported via APPLY instruction */
		Set<ActionType> tableApplyActions = new HashSet<ActionType>();
		tableApplyActions.add(ActionType.OUTPUT);
		tableApplyActions.add(ActionType.SET_FIELD);
		tableFeatures.addProp(TableFeatureFactory.createActionProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.APPLY_ACTIONS, tableApplyActions));

		/*
		 * Set the actions to be supported via APPLY instruction for a table
		 * miss rule
		 */
		Set<ActionType> tableApplyActionsMiss = new HashSet<ActionType>();
		tableApplyActionsMiss.add(ActionType.OUTPUT);
		tableApplyActionsMiss.add(ActionType.SET_FIELD);
		tableFeatures.addProp(TableFeatureFactory.createActionProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.APPLY_ACTIONS_MISS, tableApplyActionsMiss));

		/* Set the fields the table will match on */
		Map<OxmBasicFieldType, Boolean> tableMatchFields = new HashMap<OxmBasicFieldType, Boolean>();
		tableMatchFields.put(OxmBasicFieldType.ETH_TYPE, false);
		tableMatchFields.put(OxmBasicFieldType.IP_PROTO, false);
		tableMatchFields.put(OxmBasicFieldType.IPV6_FLABEL, true);
		tableFeatures.addProp(
				TableFeatureFactory.createOxmProp(ProtocolVersion.V_1_3, TableFeaturePropType.MATCH, tableMatchFields));

		/* Set the match fields that can be wildcarded */
		Map<OxmBasicFieldType, Boolean> tableWildcards = new HashMap<OxmBasicFieldType, Boolean>();
		tableWildcards.put(OxmBasicFieldType.ETH_TYPE, false);
		tableWildcards.put(OxmBasicFieldType.IP_PROTO, false);
		tableWildcards.put(OxmBasicFieldType.IPV6_FLABEL, false);
		tableFeatures.addProp(TableFeatureFactory.createOxmProp(ProtocolVersion.V_1_3, TableFeaturePropType.WILDCARDS,
				tableWildcards));

		/* Set the fields that can be modified via a WRITE instruction */
		Map<OxmBasicFieldType, Boolean> tableWriteSetFields = new HashMap<OxmBasicFieldType, Boolean>();
		tableWriteSetFields.put(OxmBasicFieldType.VLAN_VID, false);
		tableWriteSetFields.put(OxmBasicFieldType.VLAN_PCP, false);
		tableWriteSetFields.put(OxmBasicFieldType.ETH_DST, false);
		tableWriteSetFields.put(OxmBasicFieldType.ETH_SRC, false);
		tableFeatures.addProp(TableFeatureFactory.createOxmProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.WRITE_SETFIELD, tableWriteSetFields));

		/*
		 * Set the fields that can be modified via a WRITE instruction for a
		 * miss rule
		 */
		Map<OxmBasicFieldType, Boolean> tableWriteSetFieldsMiss = new HashMap<OxmBasicFieldType, Boolean>();
		tableWriteSetFieldsMiss.put(OxmBasicFieldType.VLAN_VID, false);
		tableWriteSetFieldsMiss.put(OxmBasicFieldType.VLAN_PCP, false);
		tableWriteSetFieldsMiss.put(OxmBasicFieldType.ETH_DST, false);
		tableWriteSetFieldsMiss.put(OxmBasicFieldType.ETH_SRC, false);
		tableFeatures.addProp(TableFeatureFactory.createOxmProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.WRITE_SETFIELD_MISS, tableWriteSetFieldsMiss));

		/* Set the fields that can be modified via a APPLY instruction */
		Map<OxmBasicFieldType, Boolean> tableApplySetFields = new HashMap<OxmBasicFieldType, Boolean>();
		tableApplySetFields.put(OxmBasicFieldType.VLAN_VID, false);
		tableApplySetFields.put(OxmBasicFieldType.VLAN_PCP, false);
		tableApplySetFields.put(OxmBasicFieldType.ETH_DST, false);
		tableApplySetFields.put(OxmBasicFieldType.ETH_SRC, false);
		tableFeatures.addProp(TableFeatureFactory.createOxmProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.APPLY_SETFIELD, tableApplySetFields));

		/*
		 * Set the fields that can be modified via a APPLY instruction for a
		 * miss rule
		 */
		Map<OxmBasicFieldType, Boolean> tableApplySetFieldsMiss = new HashMap<OxmBasicFieldType, Boolean>();
		tableApplySetFieldsMiss.put(OxmBasicFieldType.VLAN_VID, false);
		tableApplySetFieldsMiss.put(OxmBasicFieldType.VLAN_PCP, false);
		tableApplySetFieldsMiss.put(OxmBasicFieldType.ETH_DST, false);
		tableApplySetFieldsMiss.put(OxmBasicFieldType.ETH_SRC, false);
		tableFeatures.addProp(TableFeatureFactory.createOxmProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.APPLY_SETFIELD_MISS, tableApplySetFieldsMiss));

		return (MBodyTableFeatures) tableFeatures.toImmutable();
	}

	public MBodyTableFeatures createMACTableProperties() {

		/*
		 * Now let us create a Table Features Request message with a new
		 * pipeline and send it to the switch
		 */

		MBodyMutableTableFeatures tableFeatures = new MBodyMutableTableFeatures(ProtocolVersion.V_1_3);

		/* Set the table ID for the new table */
		TableId tblID = TableId.valueOf(0);
		tableFeatures.tableId(tblID);

		/* Set a name for the new table */
		tableFeatures.name("OpenFlow MAC Table");

		/* Set the metadata match & metadata write fields for the table */
		tableFeatures.metadataWrite(0x00);
		tableFeatures.metadataMatch(0x00);

		/* Set the size of the new table */
		tableFeatures.maxEntries(2048);

		/* Set the instructions to be supported in the new table */
		Set<InstructionType> tableInstrTypes = new HashSet<InstructionType>();
		tableInstrTypes.add(InstructionType.APPLY_ACTIONS);
		tableInstrTypes.add(InstructionType.WRITE_ACTIONS);
		tableInstrTypes.add(InstructionType.GOTO_TABLE);
		tableInstrTypes.add(InstructionType.METER);

		tableFeatures.addProp(TableFeatureFactory.createInstrProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.INSTRUCTIONS, tableInstrTypes));

		/* Set the instructions to be supported for the table miss rule */
		Set<InstructionType> tableInstrMissTypes = new HashSet<InstructionType>();
		tableInstrMissTypes.add(InstructionType.APPLY_ACTIONS);
		tableInstrMissTypes.add(InstructionType.WRITE_ACTIONS);
		tableInstrMissTypes.add(InstructionType.GOTO_TABLE);
		tableInstrMissTypes.add(InstructionType.METER);

		tableFeatures.addProp(TableFeatureFactory.createInstrProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.INSTRUCTIONS_MISS, tableInstrMissTypes));

		/*
		 * Set the tables to which rules can be pointed to from the current
		 * table
		 */
		Set<TableId> tableNextTables = new HashSet<TableId>();
		tableNextTables.add(TableId.valueOf(1));
		tableNextTables.add(TableId.valueOf(2));
		tableNextTables.add(TableId.valueOf(3));
		tableNextTables.add(TableId.valueOf(4));
		tableNextTables.add(TableId.valueOf(5));
		tableFeatures.addProp(TableFeatureFactory.createNextTablesProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.NEXT_TABLES, tableNextTables));

		/*
		 * Set the tables to which the table miss rule can be point to from the
		 * current table
		 */
		Set<TableId> tableNextTablesMiss = new HashSet<TableId>();
		tableNextTablesMiss.add(TableId.valueOf(1));
		tableNextTablesMiss.add(TableId.valueOf(2));
		tableNextTablesMiss.add(TableId.valueOf(3));
		tableNextTablesMiss.add(TableId.valueOf(4));
		tableNextTablesMiss.add(TableId.valueOf(5));
		tableFeatures.addProp(TableFeatureFactory.createNextTablesProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.NEXT_TABLES_MISS, tableNextTablesMiss));

		/* Set the actions to be supported via WRITE instruction */
		Set<ActionType> tableWriteActions = new HashSet<ActionType>();
		tableWriteActions.add(ActionType.OUTPUT);
		tableWriteActions.add(ActionType.SET_FIELD);
		tableFeatures.addProp(TableFeatureFactory.createActionProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.WRITE_ACTIONS, tableWriteActions));

		/*
		 * Set the actions to be supported via WRITE instruction for a table
		 * miss rule
		 */
		Set<ActionType> tableWriteActionsMiss = new HashSet<ActionType>();
		tableWriteActionsMiss.add(ActionType.OUTPUT);
		tableWriteActionsMiss.add(ActionType.SET_FIELD);
		tableFeatures.addProp(TableFeatureFactory.createActionProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.WRITE_ACTIONS_MISS, tableWriteActionsMiss));

		/* Set the actions to be supported via APPLY instruction */
		Set<ActionType> tableApplyActions = new HashSet<ActionType>();
		tableApplyActions.add(ActionType.OUTPUT);
		tableApplyActions.add(ActionType.SET_FIELD);
		tableFeatures.addProp(TableFeatureFactory.createActionProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.APPLY_ACTIONS, tableApplyActions));

		/*
		 * Set the actions to be supported via APPLY instruction for a table
		 * miss rule
		 */
		Set<ActionType> tableApplyActionsMiss = new HashSet<ActionType>();
		tableApplyActionsMiss.add(ActionType.OUTPUT);
		tableApplyActionsMiss.add(ActionType.SET_FIELD);
		tableFeatures.addProp(TableFeatureFactory.createActionProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.APPLY_ACTIONS_MISS, tableApplyActionsMiss));

		/* Set the fields the table will match on */
		Map<OxmBasicFieldType, Boolean> tableMatchFields = new HashMap<OxmBasicFieldType, Boolean>();
		tableMatchFields.put(OxmBasicFieldType.ETH_TYPE, false);
		tableMatchFields.put(OxmBasicFieldType.VLAN_VID, false);
		tableMatchFields.put(OxmBasicFieldType.ETH_SRC, false);
		tableMatchFields.put(OxmBasicFieldType.ETH_DST, false);
		tableFeatures.addProp(
				TableFeatureFactory.createOxmProp(ProtocolVersion.V_1_3, TableFeaturePropType.MATCH, tableMatchFields));

		/* Set the match fields that can be wildcarded */
		Map<OxmBasicFieldType, Boolean> tableWildcards = new HashMap<OxmBasicFieldType, Boolean>();
		tableFeatures.addProp(TableFeatureFactory.createOxmProp(ProtocolVersion.V_1_3, TableFeaturePropType.WILDCARDS,
				tableWildcards));

		/* Set the fields that can be modified via a WRITE instruction */
		Map<OxmBasicFieldType, Boolean> tableWriteSetFields = new HashMap<OxmBasicFieldType, Boolean>();
		tableWriteSetFields.put(OxmBasicFieldType.VLAN_VID, false);
		tableWriteSetFields.put(OxmBasicFieldType.VLAN_PCP, false);
		tableWriteSetFields.put(OxmBasicFieldType.ETH_DST, false);
		tableWriteSetFields.put(OxmBasicFieldType.ETH_SRC, false);
		tableFeatures.addProp(TableFeatureFactory.createOxmProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.WRITE_SETFIELD, tableWriteSetFields));

		/*
		 * Set the fields that can be modified via a WRITE instruction for a
		 * miss rule
		 */
		Map<OxmBasicFieldType, Boolean> tableWriteSetFieldsMiss = new HashMap<OxmBasicFieldType, Boolean>();
		tableWriteSetFieldsMiss.put(OxmBasicFieldType.VLAN_VID, false);
		tableWriteSetFieldsMiss.put(OxmBasicFieldType.VLAN_PCP, false);
		tableWriteSetFieldsMiss.put(OxmBasicFieldType.ETH_DST, false);
		tableWriteSetFieldsMiss.put(OxmBasicFieldType.ETH_SRC, false);
		tableFeatures.addProp(TableFeatureFactory.createOxmProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.WRITE_SETFIELD_MISS, tableWriteSetFieldsMiss));

		/* Set the fields that can be modified via a APPLY instruction */
		Map<OxmBasicFieldType, Boolean> tableApplySetFields = new HashMap<OxmBasicFieldType, Boolean>();
		tableApplySetFields.put(OxmBasicFieldType.VLAN_VID, false);
		tableApplySetFields.put(OxmBasicFieldType.VLAN_PCP, false);
		tableApplySetFields.put(OxmBasicFieldType.ETH_DST, false);
		tableApplySetFields.put(OxmBasicFieldType.ETH_SRC, false);
		tableFeatures.addProp(TableFeatureFactory.createOxmProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.APPLY_SETFIELD, tableApplySetFields));

		/*
		 * Set the fields that can be modified via a APPLY instruction for a
		 * miss rule
		 */
		Map<OxmBasicFieldType, Boolean> tableApplySetFieldsMiss = new HashMap<OxmBasicFieldType, Boolean>();
		tableApplySetFieldsMiss.put(OxmBasicFieldType.VLAN_VID, false);
		tableApplySetFieldsMiss.put(OxmBasicFieldType.VLAN_PCP, false);
		tableApplySetFieldsMiss.put(OxmBasicFieldType.ETH_DST, false);
		tableApplySetFieldsMiss.put(OxmBasicFieldType.ETH_SRC, false);
		tableFeatures.addProp(TableFeatureFactory.createOxmProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.APPLY_SETFIELD_MISS, tableApplySetFieldsMiss));

		return (MBodyTableFeatures) tableFeatures.toImmutable();
	}

	public MBodyTableFeatures createARPTableProperties() {

		/*
		 * Now let us create a Table Features Request message with a new
		 * pipeline and send it to the switch
		 */

		MBodyMutableTableFeatures tableFeatures = new MBodyMutableTableFeatures(ProtocolVersion.V_1_3);

		/* Set the table ID for the new table */
		TableId tblID = TableId.valueOf(1);
		tableFeatures.tableId(tblID);

		/* Set a name for the new table */
		tableFeatures.name("OpenFlow ARP Table");

		/* Set the metadata match & metadata write fields for the table */
		tableFeatures.metadataWrite(0x00);
		tableFeatures.metadataMatch(0x00);

		/* Set the size of the new table */
		tableFeatures.maxEntries(2048);

		/* Set the instructions to be supported in the new table */
		Set<InstructionType> tableInstrTypes = new HashSet<InstructionType>();
		tableInstrTypes.add(InstructionType.APPLY_ACTIONS);
		tableInstrTypes.add(InstructionType.WRITE_ACTIONS);
		tableInstrTypes.add(InstructionType.METER);

		tableFeatures.addProp(TableFeatureFactory.createInstrProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.INSTRUCTIONS, tableInstrTypes));

		/* Set the instructions to be supported for the table miss rule */
		Set<InstructionType> tableInstrMissTypes = new HashSet<InstructionType>();
		tableInstrMissTypes.add(InstructionType.APPLY_ACTIONS);
		tableInstrMissTypes.add(InstructionType.WRITE_ACTIONS);
		tableInstrMissTypes.add(InstructionType.METER);

		tableFeatures.addProp(TableFeatureFactory.createInstrProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.INSTRUCTIONS_MISS, tableInstrMissTypes));

		/*
		 * Set the tables to which rules can be pointed to from the current
		 * table
		 */
		Set<TableId> tableNextTables = new HashSet<TableId>();
		tableFeatures.addProp(TableFeatureFactory.createNextTablesProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.NEXT_TABLES, tableNextTables));

		/*
		 * Set the tables to which the table miss rule can be point to from the
		 * current table
		 */
		Set<TableId> tableNextTablesMiss = new HashSet<TableId>();
		tableFeatures.addProp(TableFeatureFactory.createNextTablesProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.NEXT_TABLES_MISS, tableNextTablesMiss));

		/* Set the actions to be supported via WRITE instruction */
		Set<ActionType> tableWriteActions = new HashSet<ActionType>();
		tableWriteActions.add(ActionType.OUTPUT);
		tableWriteActions.add(ActionType.GROUP);
		tableWriteActions.add(ActionType.SET_FIELD);
		tableFeatures.addProp(TableFeatureFactory.createActionProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.WRITE_ACTIONS, tableWriteActions));

		/*
		 * Set the actions to be supported via WRITE instruction for a table
		 * miss rule
		 */
		Set<ActionType> tableWriteActionsMiss = new HashSet<ActionType>();
		tableWriteActionsMiss.add(ActionType.OUTPUT);
		tableWriteActionsMiss.add(ActionType.GROUP);
		tableWriteActionsMiss.add(ActionType.SET_FIELD);
		tableFeatures.addProp(TableFeatureFactory.createActionProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.WRITE_ACTIONS_MISS, tableWriteActionsMiss));

		/* Set the actions to be supported via APPLY instruction */
		Set<ActionType> tableApplyActions = new HashSet<ActionType>();
		tableApplyActions.add(ActionType.OUTPUT);
		tableApplyActions.add(ActionType.GROUP);
		tableApplyActions.add(ActionType.SET_FIELD);
		tableFeatures.addProp(TableFeatureFactory.createActionProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.APPLY_ACTIONS, tableApplyActions));

		/*
		 * Set the actions to be supported via APPLY instruction for a table
		 * miss rule
		 */
		Set<ActionType> tableApplyActionsMiss = new HashSet<ActionType>();
		tableApplyActionsMiss.add(ActionType.OUTPUT);
		tableApplyActionsMiss.add(ActionType.GROUP);
		tableApplyActionsMiss.add(ActionType.SET_FIELD);
		tableFeatures.addProp(TableFeatureFactory.createActionProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.APPLY_ACTIONS_MISS, tableApplyActionsMiss));

		/* Set the fields the table will match on */
		Map<OxmBasicFieldType, Boolean> tableMatchFields = new HashMap<OxmBasicFieldType, Boolean>();
		tableMatchFields.put(OxmBasicFieldType.ETH_TYPE, false);
		tableMatchFields.put(OxmBasicFieldType.ETH_DST, false);
		tableMatchFields.put(OxmBasicFieldType.ETH_SRC, false);
		tableMatchFields.put(OxmBasicFieldType.ARP_OP, false);
		tableFeatures.addProp(
				TableFeatureFactory.createOxmProp(ProtocolVersion.V_1_3, TableFeaturePropType.MATCH, tableMatchFields));

		/* Set the match fields that can be wildcarded */
		Map<OxmBasicFieldType, Boolean> tableWildcards = new HashMap<OxmBasicFieldType, Boolean>();
		tableFeatures.addProp(TableFeatureFactory.createOxmProp(ProtocolVersion.V_1_3, TableFeaturePropType.WILDCARDS,
				tableWildcards));

		/* Set the fields that can be modified via a WRITE instruction */
		Map<OxmBasicFieldType, Boolean> tableWriteSetFields = new HashMap<OxmBasicFieldType, Boolean>();
		tableWriteSetFields.put(OxmBasicFieldType.ETH_SRC, false);
		tableWriteSetFields.put(OxmBasicFieldType.ETH_DST, false);
		tableWriteSetFields.put(OxmBasicFieldType.VLAN_VID, false);
		tableWriteSetFields.put(OxmBasicFieldType.VLAN_PCP, false);
		tableFeatures.addProp(TableFeatureFactory.createOxmProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.WRITE_SETFIELD, tableWriteSetFields));

		/*
		 * Set the fields that can be modified via a WRITE instruction for a
		 * miss rule
		 */
		Map<OxmBasicFieldType, Boolean> tableWriteSetFieldsMiss = new HashMap<OxmBasicFieldType, Boolean>();
		tableWriteSetFieldsMiss.put(OxmBasicFieldType.ETH_SRC, false);
		tableWriteSetFieldsMiss.put(OxmBasicFieldType.ETH_DST, false);
		tableWriteSetFieldsMiss.put(OxmBasicFieldType.VLAN_VID, false);
		tableWriteSetFieldsMiss.put(OxmBasicFieldType.VLAN_PCP, false);
		tableFeatures.addProp(TableFeatureFactory.createOxmProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.WRITE_SETFIELD_MISS, tableWriteSetFieldsMiss));

		/* Set the fields that can be modified via a APPLY instruction */
		Map<OxmBasicFieldType, Boolean> tableApplySetFields = new HashMap<OxmBasicFieldType, Boolean>();
		tableApplySetFields.put(OxmBasicFieldType.ETH_SRC, false);
		tableApplySetFields.put(OxmBasicFieldType.ETH_DST, false);
		tableApplySetFields.put(OxmBasicFieldType.VLAN_VID, false);
		tableApplySetFields.put(OxmBasicFieldType.VLAN_PCP, false);
		tableFeatures.addProp(TableFeatureFactory.createOxmProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.APPLY_SETFIELD, tableApplySetFields));

		/*
		 * Set the fields that can be modified via a APPLY instruction for a
		 * miss rule
		 */
		Map<OxmBasicFieldType, Boolean> tableApplySetFieldsMiss = new HashMap<OxmBasicFieldType, Boolean>();
		tableApplySetFieldsMiss.put(OxmBasicFieldType.ETH_SRC, false);
		tableApplySetFieldsMiss.put(OxmBasicFieldType.ETH_DST, false);
		tableApplySetFieldsMiss.put(OxmBasicFieldType.VLAN_VID, false);
		tableApplySetFieldsMiss.put(OxmBasicFieldType.VLAN_PCP, false);
		tableFeatures.addProp(TableFeatureFactory.createOxmProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.APPLY_SETFIELD_MISS, tableApplySetFieldsMiss));

		return (MBodyTableFeatures) tableFeatures.toImmutable();
	}

	public MBodyTableFeatures createIPTableProperties() {

		/*
		 * Now let us create a Table Features Request message with a new
		 * pipeline and send it to the switch
		 */

		MBodyMutableTableFeatures tableFeatures = new MBodyMutableTableFeatures(ProtocolVersion.V_1_3);

		/* Set the table ID for the new table */
		TableId tblID = TableId.valueOf(2);
		tableFeatures.tableId(tblID);

		/* Set a name for the new table */
		tableFeatures.name("OpenFlow IP Table");

		/* Set the metadata match & metadata write fields for the table */
		tableFeatures.metadataWrite(0x00);
		tableFeatures.metadataMatch(0x00);

		/* Set the size of the new table */
		tableFeatures.maxEntries(128);

		/* Set the instructions to be supported in the new table */
		Set<InstructionType> tableInstrTypes = new HashSet<InstructionType>();
		tableInstrTypes.add(InstructionType.APPLY_ACTIONS);
		tableInstrTypes.add(InstructionType.WRITE_ACTIONS);
		tableInstrTypes.add(InstructionType.GOTO_TABLE);
		tableInstrTypes.add(InstructionType.METER);

		tableFeatures.addProp(TableFeatureFactory.createInstrProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.INSTRUCTIONS, tableInstrTypes));

		/* Set the instructions to be supported for the table miss rule */
		Set<InstructionType> tableInstrMissTypes = new HashSet<InstructionType>();
		tableInstrMissTypes.add(InstructionType.APPLY_ACTIONS);
		tableInstrMissTypes.add(InstructionType.WRITE_ACTIONS);
		tableInstrMissTypes.add(InstructionType.GOTO_TABLE);
		tableInstrMissTypes.add(InstructionType.METER);

		tableFeatures.addProp(TableFeatureFactory.createInstrProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.INSTRUCTIONS_MISS, tableInstrMissTypes));

		/*
		 * Set the tables to which rules can be pointed to from the current
		 * table
		 */
		Set<TableId> tableNextTables = new HashSet<TableId>();
		tableNextTables.add(TableId.valueOf(3));
		tableNextTables.add(TableId.valueOf(4));
		tableFeatures.addProp(TableFeatureFactory.createNextTablesProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.NEXT_TABLES, tableNextTables));

		/*
		 * Set the tables to which the table miss rule can be point to from the
		 * current table
		 */
		Set<TableId> tableNextTablesMiss = new HashSet<TableId>();
		tableNextTablesMiss.add(TableId.valueOf(3));
		tableNextTablesMiss.add(TableId.valueOf(4));
		tableFeatures.addProp(TableFeatureFactory.createNextTablesProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.NEXT_TABLES_MISS, tableNextTablesMiss));

		/* Set the actions to be supported via WRITE instruction */
		Set<ActionType> tableWriteActions = new HashSet<ActionType>();
		tableWriteActions.add(ActionType.OUTPUT);
		tableWriteActions.add(ActionType.SET_FIELD);
		tableFeatures.addProp(TableFeatureFactory.createActionProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.WRITE_ACTIONS, tableWriteActions));

		/*
		 * Set the actions to be supported via WRITE instruction for a table
		 * miss rule
		 */
		Set<ActionType> tableWriteActionsMiss = new HashSet<ActionType>();
		tableWriteActionsMiss.add(ActionType.OUTPUT);
		tableWriteActionsMiss.add(ActionType.SET_FIELD);
		tableFeatures.addProp(TableFeatureFactory.createActionProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.WRITE_ACTIONS_MISS, tableWriteActionsMiss));

		/* Set the actions to be supported via APPLY instruction */
		Set<ActionType> tableApplyActions = new HashSet<ActionType>();
		tableApplyActions.add(ActionType.OUTPUT);
		tableApplyActions.add(ActionType.SET_FIELD);
		tableFeatures.addProp(TableFeatureFactory.createActionProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.APPLY_ACTIONS, tableApplyActions));

		/*
		 * Set the actions to be supported via APPLY instruction for a table
		 * miss rule
		 */
		Set<ActionType> tableApplyActionsMiss = new HashSet<ActionType>();
		tableApplyActionsMiss.add(ActionType.OUTPUT);
		tableApplyActionsMiss.add(ActionType.SET_FIELD);
		tableFeatures.addProp(TableFeatureFactory.createActionProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.APPLY_ACTIONS_MISS, tableApplyActionsMiss));

		/* Set the fields the table will match on */
		Map<OxmBasicFieldType, Boolean> tableMatchFields = new HashMap<OxmBasicFieldType, Boolean>();
		tableMatchFields.put(OxmBasicFieldType.ETH_TYPE, false);
		tableMatchFields.put(OxmBasicFieldType.ETH_DST, false);
		tableMatchFields.put(OxmBasicFieldType.ETH_SRC, false);
		tableMatchFields.put(OxmBasicFieldType.IP_PROTO, false);
		tableMatchFields.put(OxmBasicFieldType.IPV4_DST, false);
		tableMatchFields.put(OxmBasicFieldType.IPV4_SRC, false);
		tableMatchFields.put(OxmBasicFieldType.IPV6_SRC, false);
		tableMatchFields.put(OxmBasicFieldType.IPV6_DST, false);
		tableMatchFields.put(OxmBasicFieldType.TCP_DST, false);
		tableMatchFields.put(OxmBasicFieldType.TCP_SRC, false);
		tableMatchFields.put(OxmBasicFieldType.UDP_DST, false);
		tableMatchFields.put(OxmBasicFieldType.UDP_SRC, false);
		tableFeatures.addProp(
				TableFeatureFactory.createOxmProp(ProtocolVersion.V_1_3, TableFeaturePropType.MATCH, tableMatchFields));

		/* Set the match fields that can be wildcarded */
		Map<OxmBasicFieldType, Boolean> tableWildcards = new HashMap<OxmBasicFieldType, Boolean>();
		tableWildcards.put(OxmBasicFieldType.ETH_DST, false);
		tableWildcards.put(OxmBasicFieldType.ETH_SRC, false);
		tableWildcards.put(OxmBasicFieldType.IP_PROTO, false);
		tableWildcards.put(OxmBasicFieldType.IPV4_DST, false);
		tableWildcards.put(OxmBasicFieldType.IPV4_SRC, false);
		tableWildcards.put(OxmBasicFieldType.IPV6_SRC, false);
		tableWildcards.put(OxmBasicFieldType.IPV6_DST, false);
		tableWildcards.put(OxmBasicFieldType.TCP_DST, false);
		tableWildcards.put(OxmBasicFieldType.TCP_SRC, false);
		tableWildcards.put(OxmBasicFieldType.UDP_DST, false);
		tableWildcards.put(OxmBasicFieldType.UDP_SRC, false);
		tableFeatures.addProp(TableFeatureFactory.createOxmProp(ProtocolVersion.V_1_3, TableFeaturePropType.WILDCARDS,
				tableWildcards));

		/* Set the fields that can be modified via a WRITE instruction */
		Map<OxmBasicFieldType, Boolean> tableWriteSetFields = new HashMap<OxmBasicFieldType, Boolean>();
		tableWriteSetFields.put(OxmBasicFieldType.ETH_SRC, false);
		tableWriteSetFields.put(OxmBasicFieldType.ETH_DST, false);
		tableWriteSetFields.put(OxmBasicFieldType.VLAN_VID, false);
		tableWriteSetFields.put(OxmBasicFieldType.VLAN_PCP, false);
		tableWriteSetFields.put(OxmBasicFieldType.IPV4_DST, false);
		tableWriteSetFields.put(OxmBasicFieldType.IPV4_SRC, false);
		tableWriteSetFields.put(OxmBasicFieldType.TCP_DST, false);
		tableWriteSetFields.put(OxmBasicFieldType.TCP_SRC, false);
		tableWriteSetFields.put(OxmBasicFieldType.UDP_DST, false);
		tableWriteSetFields.put(OxmBasicFieldType.UDP_SRC, false);
		tableFeatures.addProp(TableFeatureFactory.createOxmProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.WRITE_SETFIELD, tableWriteSetFields));

		/*
		 * Set the fields that can be modified via a WRITE instruction for a
		 * miss rule
		 */
		Map<OxmBasicFieldType, Boolean> tableWriteSetFieldsMiss = new HashMap<OxmBasicFieldType, Boolean>();
		tableWriteSetFieldsMiss.put(OxmBasicFieldType.ETH_SRC, false);
		tableWriteSetFieldsMiss.put(OxmBasicFieldType.ETH_DST, false);
		tableWriteSetFieldsMiss.put(OxmBasicFieldType.VLAN_VID, false);
		tableWriteSetFieldsMiss.put(OxmBasicFieldType.VLAN_PCP, false);
		tableWriteSetFieldsMiss.put(OxmBasicFieldType.IPV4_DST, false);
		tableWriteSetFieldsMiss.put(OxmBasicFieldType.IPV4_SRC, false);
		tableWriteSetFieldsMiss.put(OxmBasicFieldType.TCP_DST, false);
		tableWriteSetFieldsMiss.put(OxmBasicFieldType.TCP_SRC, false);
		tableWriteSetFieldsMiss.put(OxmBasicFieldType.UDP_DST, false);
		tableWriteSetFieldsMiss.put(OxmBasicFieldType.UDP_SRC, false);
		tableFeatures.addProp(TableFeatureFactory.createOxmProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.WRITE_SETFIELD_MISS, tableWriteSetFieldsMiss));

		/* Set the fields that can be modified via a APPLY instruction */
		Map<OxmBasicFieldType, Boolean> tableApplySetFields = new HashMap<OxmBasicFieldType, Boolean>();
		tableApplySetFields.put(OxmBasicFieldType.ETH_SRC, false);
		tableApplySetFields.put(OxmBasicFieldType.ETH_DST, false);
		tableApplySetFields.put(OxmBasicFieldType.VLAN_VID, false);
		tableApplySetFields.put(OxmBasicFieldType.VLAN_PCP, false);
		tableApplySetFields.put(OxmBasicFieldType.IPV4_DST, false);
		tableApplySetFields.put(OxmBasicFieldType.IPV4_SRC, false);
		tableApplySetFields.put(OxmBasicFieldType.TCP_DST, false);
		tableApplySetFields.put(OxmBasicFieldType.TCP_SRC, false);
		tableApplySetFields.put(OxmBasicFieldType.UDP_DST, false);
		tableApplySetFields.put(OxmBasicFieldType.UDP_SRC, false);
		tableFeatures.addProp(TableFeatureFactory.createOxmProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.APPLY_SETFIELD, tableApplySetFields));

		/*
		 * Set the fields that can be modified via a APPLY instruction for a
		 * miss rule
		 */
		Map<OxmBasicFieldType, Boolean> tableApplySetFieldsMiss = new HashMap<OxmBasicFieldType, Boolean>();
		tableApplySetFieldsMiss.put(OxmBasicFieldType.ETH_SRC, false);
		tableApplySetFieldsMiss.put(OxmBasicFieldType.ETH_DST, false);
		tableApplySetFieldsMiss.put(OxmBasicFieldType.VLAN_VID, false);
		tableApplySetFieldsMiss.put(OxmBasicFieldType.VLAN_PCP, false);
		tableApplySetFieldsMiss.put(OxmBasicFieldType.IPV4_DST, false);
		tableApplySetFieldsMiss.put(OxmBasicFieldType.IPV4_SRC, false);
		tableApplySetFieldsMiss.put(OxmBasicFieldType.TCP_DST, false);
		tableApplySetFieldsMiss.put(OxmBasicFieldType.TCP_SRC, false);
		tableApplySetFieldsMiss.put(OxmBasicFieldType.UDP_DST, false);
		tableApplySetFieldsMiss.put(OxmBasicFieldType.UDP_SRC, false);
		tableFeatures.addProp(TableFeatureFactory.createOxmProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.APPLY_SETFIELD_MISS, tableApplySetFieldsMiss));

		return (MBodyTableFeatures) tableFeatures.toImmutable();
	}

	public MBodyTableFeatures createTCPFlagsTableProperties() {

		/*
		 * Now let us create a Table Features Request message with a new
		 * pipeline and send it to the switch
		 */

		MBodyMutableTableFeatures tableFeatures = new MBodyMutableTableFeatures(ProtocolVersion.V_1_3);

		/* Set the table ID for the new table */
		TableId tblID = TableId.valueOf(3);
		tableFeatures.tableId(tblID);

		/* Set a name for the new table */
		tableFeatures.name("OpenFlow TCP Flags Table");

		/* Set the metadata match & metadata write fields for the table */
		tableFeatures.metadataWrite(0x00);
		tableFeatures.metadataMatch(0x00);

		/* Set the size of the new table */
		tableFeatures.maxEntries(512);

		/* Set the instructions to be supported in the new table */
		Set<InstructionType> tableInstrTypes = new HashSet<InstructionType>();
		tableInstrTypes.add(InstructionType.APPLY_ACTIONS);
		tableInstrTypes.add(InstructionType.WRITE_ACTIONS);
		tableInstrTypes.add(InstructionType.GOTO_TABLE);
		tableInstrTypes.add(InstructionType.METER);

		tableFeatures.addProp(TableFeatureFactory.createInstrProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.INSTRUCTIONS, tableInstrTypes));

		/* Set the instructions to be supported for the table miss rule */
		Set<InstructionType> tableInstrMissTypes = new HashSet<InstructionType>();
		tableInstrMissTypes.add(InstructionType.APPLY_ACTIONS);
		tableInstrMissTypes.add(InstructionType.WRITE_ACTIONS);
		tableInstrMissTypes.add(InstructionType.GOTO_TABLE);
		tableInstrMissTypes.add(InstructionType.METER);

		tableFeatures.addProp(TableFeatureFactory.createInstrProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.INSTRUCTIONS_MISS, tableInstrMissTypes));

		/*
		 * Set the tables to which rules can be pointed to from the current
		 * table
		 */
		Set<TableId> tableNextTables = new HashSet<TableId>();
		tableNextTables.add(TableId.valueOf(4));
		tableFeatures.addProp(TableFeatureFactory.createNextTablesProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.NEXT_TABLES, tableNextTables));

		/*
		 * Set the tables to which the table miss rule can be point to from the
		 * current table
		 */
		Set<TableId> tableNextTablesMiss = new HashSet<TableId>();
		tableNextTables.add(TableId.valueOf(4));
		tableFeatures.addProp(TableFeatureFactory.createNextTablesProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.NEXT_TABLES_MISS, tableNextTablesMiss));

		/* Set the actions to be supported via WRITE instruction */
		Set<ActionType> tableWriteActions = new HashSet<ActionType>();
		tableWriteActions.add(ActionType.OUTPUT);
		tableWriteActions.add(ActionType.GROUP);
		tableWriteActions.add(ActionType.SET_FIELD);
		tableFeatures.addProp(TableFeatureFactory.createActionProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.WRITE_ACTIONS, tableWriteActions));

		/*
		 * Set the actions to be supported via WRITE instruction for a table
		 * miss rule
		 */
		Set<ActionType> tableWriteActionsMiss = new HashSet<ActionType>();
		tableWriteActionsMiss.add(ActionType.OUTPUT);
		tableWriteActionsMiss.add(ActionType.GROUP);
		tableWriteActionsMiss.add(ActionType.SET_FIELD);
		tableFeatures.addProp(TableFeatureFactory.createActionProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.WRITE_ACTIONS_MISS, tableWriteActionsMiss));

		/* Set the actions to be supported via APPLY instruction */
		Set<ActionType> tableApplyActions = new HashSet<ActionType>();
		tableApplyActions.add(ActionType.OUTPUT);
		tableApplyActions.add(ActionType.GROUP);
		tableApplyActions.add(ActionType.SET_FIELD);
		tableFeatures.addProp(TableFeatureFactory.createActionProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.APPLY_ACTIONS, tableApplyActions));

		/*
		 * Set the actions to be supported via APPLY instruction for a table
		 * miss rule
		 */
		Set<ActionType> tableApplyActionsMiss = new HashSet<ActionType>();
		tableApplyActionsMiss.add(ActionType.OUTPUT);
		tableApplyActionsMiss.add(ActionType.GROUP);
		tableApplyActionsMiss.add(ActionType.SET_FIELD);
		tableFeatures.addProp(TableFeatureFactory.createActionProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.APPLY_ACTIONS_MISS, tableApplyActionsMiss));

		/* Set the fields the table will match on */
		Map<OxmBasicFieldType, Boolean> tableMatchFields = new HashMap<OxmBasicFieldType, Boolean>();
		tableMatchFields.put(OxmBasicFieldType.ETH_TYPE, false);
		tableMatchFields.put(OxmBasicFieldType.IP_PROTO, false);
		tableMatchFields.put(OxmBasicFieldType.IPV4_SRC, false);
		tableMatchFields.put(OxmBasicFieldType.IPV6_SRC, false);

		List<MFieldExperimenter> tableExpMatchFields = new ArrayList<MFieldExperimenter>();
		MFieldExperimenter expMatchFieldTCPFlags = com.hp.of.lib.match.FieldFactory
				.createExperimenterField(ProtocolVersion.V_1_3, 4, 9345, null);
		tableExpMatchFields.add(expMatchFieldTCPFlags);

		tableFeatures.addProp(TableFeatureFactory.createOxmProp(ProtocolVersion.V_1_3, TableFeaturePropType.MATCH,
				tableMatchFields, tableExpMatchFields));

		/* Set the match fields that can be wildcarded */
		Map<OxmBasicFieldType, Boolean> tableWildcards = new HashMap<OxmBasicFieldType, Boolean>();
		tableWildcards.put(OxmBasicFieldType.IPV4_SRC, false);
		tableWildcards.put(OxmBasicFieldType.IPV6_SRC, false);

		List<MFieldExperimenter> tableExpWildcards = new ArrayList<MFieldExperimenter>();
		MFieldExperimenter expWildcardTCPFlags = com.hp.of.lib.match.FieldFactory
				.createExperimenterField(ProtocolVersion.V_1_3, 4, 9345, null);
		tableExpWildcards.add(expWildcardTCPFlags);

		tableFeatures.addProp(TableFeatureFactory.createOxmProp(ProtocolVersion.V_1_3, TableFeaturePropType.WILDCARDS,
				tableWildcards, tableExpWildcards));

		/* Set the fields that can be modified via a WRITE instruction */
		Map<OxmBasicFieldType, Boolean> tableWriteSetFields = new HashMap<OxmBasicFieldType, Boolean>();
		tableWriteSetFields.put(OxmBasicFieldType.ETH_SRC, false);
		tableWriteSetFields.put(OxmBasicFieldType.ETH_DST, false);
		tableWriteSetFields.put(OxmBasicFieldType.VLAN_VID, false);
		tableWriteSetFields.put(OxmBasicFieldType.VLAN_PCP, false);
		tableFeatures.addProp(TableFeatureFactory.createOxmProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.WRITE_SETFIELD, tableWriteSetFields));

		/*
		 * Set the fields that can be modified via a WRITE instruction for a
		 * miss rule
		 */
		Map<OxmBasicFieldType, Boolean> tableWriteSetFieldsMiss = new HashMap<OxmBasicFieldType, Boolean>();
		tableWriteSetFieldsMiss.put(OxmBasicFieldType.ETH_SRC, false);
		tableWriteSetFieldsMiss.put(OxmBasicFieldType.ETH_DST, false);
		tableWriteSetFieldsMiss.put(OxmBasicFieldType.VLAN_VID, false);
		tableWriteSetFieldsMiss.put(OxmBasicFieldType.VLAN_PCP, false);
		tableFeatures.addProp(TableFeatureFactory.createOxmProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.WRITE_SETFIELD_MISS, tableWriteSetFieldsMiss));

		/* Set the fields that can be modified via a APPLY instruction */
		Map<OxmBasicFieldType, Boolean> tableApplySetFields = new HashMap<OxmBasicFieldType, Boolean>();
		tableApplySetFields.put(OxmBasicFieldType.ETH_SRC, false);
		tableApplySetFields.put(OxmBasicFieldType.ETH_DST, false);
		tableApplySetFields.put(OxmBasicFieldType.VLAN_VID, false);
		tableApplySetFields.put(OxmBasicFieldType.VLAN_PCP, false);
		tableFeatures.addProp(TableFeatureFactory.createOxmProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.APPLY_SETFIELD, tableApplySetFields));

		/*
		 * Set the fields that can be modified via a APPLY instruction for a
		 * miss rule
		 */
		Map<OxmBasicFieldType, Boolean> tableApplySetFieldsMiss = new HashMap<OxmBasicFieldType, Boolean>();
		tableApplySetFieldsMiss.put(OxmBasicFieldType.ETH_SRC, false);
		tableApplySetFieldsMiss.put(OxmBasicFieldType.ETH_DST, false);
		tableApplySetFieldsMiss.put(OxmBasicFieldType.VLAN_VID, false);
		tableApplySetFieldsMiss.put(OxmBasicFieldType.VLAN_PCP, false);
		tableFeatures.addProp(TableFeatureFactory.createOxmProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.APPLY_SETFIELD_MISS, tableApplySetFieldsMiss));

		return (MBodyTableFeatures) tableFeatures.toImmutable();
	}

	public MBodyTableFeatures createL4RangesTableProperties() {

		/*
		 * Now let us create a Table Features Request message with a new
		 * pipeline and send it to the switch
		 */

		MBodyMutableTableFeatures tableFeatures = new MBodyMutableTableFeatures(ProtocolVersion.V_1_3);

		/* Set the table ID for the new table */
		TableId tblID = TableId.valueOf(4);
		tableFeatures.tableId(tblID);

		/* Set a name for the new table */
		tableFeatures.name("OpenFlow L4 Ranges Table");

		/* Set the metadata match & metadata write fields for the table */
		tableFeatures.metadataWrite(0x00);
		tableFeatures.metadataMatch(0x00);

		/* Set the size of the new table */
		tableFeatures.maxEntries(512);

		/* Set the instructions to be supported in the new table */
		Set<InstructionType> tableInstrTypes = new HashSet<InstructionType>();
		tableInstrTypes.add(InstructionType.APPLY_ACTIONS);
		tableInstrTypes.add(InstructionType.WRITE_ACTIONS);
		tableInstrTypes.add(InstructionType.GOTO_TABLE);
		tableInstrTypes.add(InstructionType.METER);

		tableFeatures.addProp(TableFeatureFactory.createInstrProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.INSTRUCTIONS, tableInstrTypes));

		/* Set the instructions to be supported for the table miss rule */
		Set<InstructionType> tableInstrMissTypes = new HashSet<InstructionType>();
		tableInstrMissTypes.add(InstructionType.APPLY_ACTIONS);
		tableInstrMissTypes.add(InstructionType.WRITE_ACTIONS);
		tableInstrMissTypes.add(InstructionType.GOTO_TABLE);
		tableInstrMissTypes.add(InstructionType.METER);

		tableFeatures.addProp(TableFeatureFactory.createInstrProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.INSTRUCTIONS_MISS, tableInstrMissTypes));

		/*
		 * Set the tables to which rules can be pointed to from the current
		 * table
		 */
		Set<TableId> tableNextTables = new HashSet<TableId>();
		tableFeatures.addProp(TableFeatureFactory.createNextTablesProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.NEXT_TABLES, tableNextTables));

		/*
		 * Set the tables to which the table miss rule can be point to from the
		 * current table
		 */
		Set<TableId> tableNextTablesMiss = new HashSet<TableId>();
		tableFeatures.addProp(TableFeatureFactory.createNextTablesProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.NEXT_TABLES_MISS, tableNextTablesMiss));

		/* Set the actions to be supported via WRITE instruction */
		Set<ActionType> tableWriteActions = new HashSet<ActionType>();
		tableWriteActions.add(ActionType.OUTPUT);
		tableWriteActions.add(ActionType.GROUP);
		tableWriteActions.add(ActionType.SET_FIELD);
		tableFeatures.addProp(TableFeatureFactory.createActionProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.WRITE_ACTIONS, tableWriteActions));

		/*
		 * Set the actions to be supported via WRITE instruction for a table
		 * miss rule
		 */
		Set<ActionType> tableWriteActionsMiss = new HashSet<ActionType>();
		tableWriteActionsMiss.add(ActionType.OUTPUT);
		tableWriteActionsMiss.add(ActionType.GROUP);
		tableWriteActionsMiss.add(ActionType.SET_FIELD);
		tableFeatures.addProp(TableFeatureFactory.createActionProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.WRITE_ACTIONS_MISS, tableWriteActionsMiss));

		/* Set the actions to be supported via APPLY instruction */
		Set<ActionType> tableApplyActions = new HashSet<ActionType>();
		tableApplyActions.add(ActionType.OUTPUT);
		tableApplyActions.add(ActionType.GROUP);
		tableApplyActions.add(ActionType.SET_FIELD);
		tableFeatures.addProp(TableFeatureFactory.createActionProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.APPLY_ACTIONS, tableApplyActions));

		/*
		 * Set the actions to be supported via APPLY instruction for a table
		 * miss rule
		 */
		Set<ActionType> tableApplyActionsMiss = new HashSet<ActionType>();
		tableApplyActionsMiss.add(ActionType.OUTPUT);
		tableApplyActionsMiss.add(ActionType.GROUP);
		tableApplyActionsMiss.add(ActionType.SET_FIELD);
		tableFeatures.addProp(TableFeatureFactory.createActionProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.APPLY_ACTIONS_MISS, tableApplyActionsMiss));

		/* Set the fields the table will match on */
		Map<OxmBasicFieldType, Boolean> tableMatchFields = new HashMap<OxmBasicFieldType, Boolean>();
		tableMatchFields.put(OxmBasicFieldType.ETH_TYPE, false);
		tableMatchFields.put(OxmBasicFieldType.IP_PROTO, false);
		tableMatchFields.put(OxmBasicFieldType.IPV4_SRC, false);
		tableMatchFields.put(OxmBasicFieldType.IPV6_SRC, false);

		List<MFieldExperimenter> tableExpMatchFields = new ArrayList<MFieldExperimenter>();
		MFieldExperimenter expMatchFieldUDPSrcPortRange = com.hp.of.lib.match.FieldFactory
				.createExperimenterField(ProtocolVersion.V_1_3, 0, 9345, null);
		MFieldExperimenter expMatchFieldUDPDstPortRange = com.hp.of.lib.match.FieldFactory
				.createExperimenterField(ProtocolVersion.V_1_3, 1, 9345, null);
		MFieldExperimenter expMatchFieldTCPSrcPortRange = com.hp.of.lib.match.FieldFactory
				.createExperimenterField(ProtocolVersion.V_1_3, 2, 9345, null);
		MFieldExperimenter expMatchFieldTCPDstPortRange = com.hp.of.lib.match.FieldFactory
				.createExperimenterField(ProtocolVersion.V_1_3, 3, 9345, null);

		tableExpMatchFields.add(expMatchFieldUDPSrcPortRange);
		tableExpMatchFields.add(expMatchFieldUDPDstPortRange);
		tableExpMatchFields.add(expMatchFieldTCPSrcPortRange);
		tableExpMatchFields.add(expMatchFieldTCPDstPortRange);

		tableFeatures.addProp(TableFeatureFactory.createOxmProp(ProtocolVersion.V_1_3, TableFeaturePropType.MATCH,
				tableMatchFields, tableExpMatchFields));

		/* Set the match fields that can be wildcarded */
		Map<OxmBasicFieldType, Boolean> tableWildcards = new HashMap<OxmBasicFieldType, Boolean>();
		tableWildcards.put(OxmBasicFieldType.IPV4_SRC, false);
		tableWildcards.put(OxmBasicFieldType.IPV6_SRC, false);

		List<MFieldExperimenter> tableExpWildcards = new ArrayList<MFieldExperimenter>();
		MFieldExperimenter expWildcardUDPSrcPortRange = com.hp.of.lib.match.FieldFactory
				.createExperimenterField(ProtocolVersion.V_1_3, 0, 9345, null);
		MFieldExperimenter expWildcardUDPDstPortRange = com.hp.of.lib.match.FieldFactory
				.createExperimenterField(ProtocolVersion.V_1_3, 1, 9345, null);
		MFieldExperimenter expWildcardTCPSrcPortRange = com.hp.of.lib.match.FieldFactory
				.createExperimenterField(ProtocolVersion.V_1_3, 2, 9345, null);
		MFieldExperimenter expWildcardTCPDstPortRange = com.hp.of.lib.match.FieldFactory
				.createExperimenterField(ProtocolVersion.V_1_3, 3, 9345, null);

		tableExpWildcards.add(expWildcardUDPSrcPortRange);
		tableExpWildcards.add(expWildcardUDPDstPortRange);
		tableExpWildcards.add(expWildcardTCPSrcPortRange);
		tableExpWildcards.add(expWildcardTCPDstPortRange);

		tableFeatures.addProp(TableFeatureFactory.createOxmProp(ProtocolVersion.V_1_3, TableFeaturePropType.WILDCARDS,
				tableWildcards, tableExpWildcards));

		/* Set the fields that can be modified via a WRITE instruction */
		Map<OxmBasicFieldType, Boolean> tableWriteSetFields = new HashMap<OxmBasicFieldType, Boolean>();
		tableWriteSetFields.put(OxmBasicFieldType.ETH_SRC, false);
		tableWriteSetFields.put(OxmBasicFieldType.ETH_DST, false);
		tableWriteSetFields.put(OxmBasicFieldType.VLAN_VID, false);
		tableWriteSetFields.put(OxmBasicFieldType.VLAN_PCP, false);
		tableFeatures.addProp(TableFeatureFactory.createOxmProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.WRITE_SETFIELD, tableWriteSetFields));

		/*
		 * Set the fields that can be modified via a WRITE instruction for a
		 * miss rule
		 */
		Map<OxmBasicFieldType, Boolean> tableWriteSetFieldsMiss = new HashMap<OxmBasicFieldType, Boolean>();
		tableWriteSetFieldsMiss.put(OxmBasicFieldType.ETH_SRC, false);
		tableWriteSetFieldsMiss.put(OxmBasicFieldType.ETH_DST, false);
		tableWriteSetFieldsMiss.put(OxmBasicFieldType.VLAN_VID, false);
		tableWriteSetFieldsMiss.put(OxmBasicFieldType.VLAN_PCP, false);
		tableFeatures.addProp(TableFeatureFactory.createOxmProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.WRITE_SETFIELD_MISS, tableWriteSetFieldsMiss));

		/* Set the fields that can be modified via a APPLY instruction */
		Map<OxmBasicFieldType, Boolean> tableApplySetFields = new HashMap<OxmBasicFieldType, Boolean>();
		tableApplySetFields.put(OxmBasicFieldType.ETH_SRC, false);
		tableApplySetFields.put(OxmBasicFieldType.ETH_DST, false);
		tableApplySetFields.put(OxmBasicFieldType.VLAN_VID, false);
		tableApplySetFields.put(OxmBasicFieldType.VLAN_PCP, false);
		tableFeatures.addProp(TableFeatureFactory.createOxmProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.APPLY_SETFIELD, tableApplySetFields));

		/*
		 * Set the fields that can be modified via a APPLY instruction for a
		 * miss rule
		 */
		Map<OxmBasicFieldType, Boolean> tableApplySetFieldsMiss = new HashMap<OxmBasicFieldType, Boolean>();
		tableApplySetFieldsMiss.put(OxmBasicFieldType.ETH_SRC, false);
		tableApplySetFieldsMiss.put(OxmBasicFieldType.ETH_DST, false);
		tableApplySetFieldsMiss.put(OxmBasicFieldType.VLAN_VID, false);
		tableApplySetFieldsMiss.put(OxmBasicFieldType.VLAN_PCP, false);
		tableFeatures.addProp(TableFeatureFactory.createOxmProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.APPLY_SETFIELD_MISS, tableApplySetFieldsMiss));

		return (MBodyTableFeatures) tableFeatures.toImmutable();
	}

	public MBodyTableFeatures createCustomMatchTableProperties() {

		/*
		 * Now let us create a Table Features Request message with a new
		 * pipeline and send it to the switch
		 */

		MBodyMutableTableFeatures tableFeatures = new MBodyMutableTableFeatures(ProtocolVersion.V_1_3);

		/* Set the table ID for the new table */
		TableId tblID = TableId.valueOf(6);
		tableFeatures.tableId(tblID);

		/* Set a name for the new table */
		tableFeatures.name("OpenFlow Custom Match Table");

		/* Set the metadata match & metadata write fields for the table */
		tableFeatures.metadataWrite(0x00);
		tableFeatures.metadataMatch(0x00);

		/* Set the size of the new table */
		tableFeatures.maxEntries(512);

		/* Set the instructions to be supported in the new table */
		Set<InstructionType> tableInstrTypes = new HashSet<InstructionType>();
		tableInstrTypes.add(InstructionType.APPLY_ACTIONS);
		tableInstrTypes.add(InstructionType.WRITE_ACTIONS);
		tableInstrTypes.add(InstructionType.GOTO_TABLE);
		tableInstrTypes.add(InstructionType.METER);

		tableFeatures.addProp(TableFeatureFactory.createInstrProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.INSTRUCTIONS, tableInstrTypes));

		/* Set the instructions to be supported for the table miss rule */
		Set<InstructionType> tableInstrMissTypes = new HashSet<InstructionType>();
		tableInstrMissTypes.add(InstructionType.APPLY_ACTIONS);
		tableInstrMissTypes.add(InstructionType.WRITE_ACTIONS);
		tableInstrMissTypes.add(InstructionType.GOTO_TABLE);
		tableInstrMissTypes.add(InstructionType.METER);

		tableFeatures.addProp(TableFeatureFactory.createInstrProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.INSTRUCTIONS_MISS, tableInstrMissTypes));

		/*
		 * Set the tables to which rules can be pointed to from the current
		 * table
		 */
		Set<TableId> tableNextTables = new HashSet<TableId>();
		tableNextTables.add(TableId.valueOf(7));
		tableFeatures.addProp(TableFeatureFactory.createNextTablesProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.NEXT_TABLES, tableNextTables));

		/*
		 * Set the tables to which the table miss rule can be point to from the
		 * current table
		 */
		Set<TableId> tableNextTablesMiss = new HashSet<TableId>();
		tableNextTablesMiss.add(TableId.valueOf(7));
		tableFeatures.addProp(TableFeatureFactory.createNextTablesProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.NEXT_TABLES_MISS, tableNextTablesMiss));

		/* Set the actions to be supported via WRITE instruction */
		Set<ActionType> tableWriteActions = new HashSet<ActionType>();
		tableWriteActions.add(ActionType.OUTPUT);
		tableWriteActions.add(ActionType.GROUP);
		tableWriteActions.add(ActionType.SET_FIELD);
		tableFeatures.addProp(TableFeatureFactory.createActionProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.WRITE_ACTIONS, tableWriteActions));

		/*
		 * Set the actions to be supported via WRITE instruction for a table
		 * miss rule
		 */
		Set<ActionType> tableWriteActionsMiss = new HashSet<ActionType>();
		tableWriteActionsMiss.add(ActionType.OUTPUT);
		tableWriteActionsMiss.add(ActionType.GROUP);
		tableWriteActionsMiss.add(ActionType.SET_FIELD);
		tableFeatures.addProp(TableFeatureFactory.createActionProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.WRITE_ACTIONS_MISS, tableWriteActionsMiss));

		/* Set the actions to be supported via APPLY instruction */
		Set<ActionType> tableApplyActions = new HashSet<ActionType>();
		tableApplyActions.add(ActionType.OUTPUT);
		tableApplyActions.add(ActionType.GROUP);
		tableApplyActions.add(ActionType.SET_FIELD);
		tableFeatures.addProp(TableFeatureFactory.createActionProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.APPLY_ACTIONS, tableApplyActions));

		/*
		 * Set the actions to be supported via APPLY instruction for a table
		 * miss rule
		 */
		Set<ActionType> tableApplyActionsMiss = new HashSet<ActionType>();
		tableApplyActionsMiss.add(ActionType.OUTPUT);
		tableApplyActionsMiss.add(ActionType.GROUP);
		tableApplyActionsMiss.add(ActionType.SET_FIELD);
		tableFeatures.addProp(TableFeatureFactory.createActionProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.APPLY_ACTIONS_MISS, tableApplyActionsMiss));

		/* Set the fields the table will match on */
		Map<OxmBasicFieldType, Boolean> tableMatchFields = new HashMap<OxmBasicFieldType, Boolean>();
		tableMatchFields.put(OxmBasicFieldType.ETH_TYPE, false);
		tableMatchFields.put(OxmBasicFieldType.IP_PROTO, false);

		List<MFieldExperimenter> tableExpMatchFields = new ArrayList<MFieldExperimenter>();
		byte[] customData1 = new byte[] { 0x00, 0x03, 0x00, 0x04, 0x00, 0x04 };

		MFieldExperimenter expMatchFieldTCPSequenceNumber = com.hp.of.lib.match.FieldFactory
				.createExperimenterField(ProtocolVersion.V_1_3, 5, 9345, customData1);

		byte[] customData2 = new byte[] { 0x00, 0x03, 0x00, 0x08, 0x00, 0x04 };

		MFieldExperimenter expMatchFieldTCPAckNumber = com.hp.of.lib.match.FieldFactory
				.createExperimenterField(ProtocolVersion.V_1_3, 6, 9345, customData2);

		tableExpMatchFields.add(expMatchFieldTCPSequenceNumber);
		tableExpMatchFields.add(expMatchFieldTCPAckNumber);

		tableFeatures.addProp(TableFeatureFactory.createOxmProp(ProtocolVersion.V_1_3, TableFeaturePropType.MATCH,
				tableMatchFields, tableExpMatchFields));

		/* Set the match fields that can be wildcarded */
		Map<OxmBasicFieldType, Boolean> tableWildcards = new HashMap<OxmBasicFieldType, Boolean>();
		tableWildcards.put(OxmBasicFieldType.IP_PROTO, false);

		List<MFieldExperimenter> tableExpWildcards = new ArrayList<MFieldExperimenter>();
		MFieldExperimenter expWildcardTCPSequenceNumber = com.hp.of.lib.match.FieldFactory
				.createExperimenterField(ProtocolVersion.V_1_3, 5, 9345, null);
		MFieldExperimenter expWildcardTCPAckNumber = com.hp.of.lib.match.FieldFactory
				.createExperimenterField(ProtocolVersion.V_1_3, 6, 9345, null);

		tableExpWildcards.add(expWildcardTCPSequenceNumber);
		tableExpWildcards.add(expWildcardTCPAckNumber);

		tableFeatures.addProp(TableFeatureFactory.createOxmProp(ProtocolVersion.V_1_3, TableFeaturePropType.WILDCARDS,
				tableWildcards, tableExpWildcards));

		/* Set the fields that can be modified via a WRITE instruction */
		Map<OxmBasicFieldType, Boolean> tableWriteSetFields = new HashMap<OxmBasicFieldType, Boolean>();
		tableWriteSetFields.put(OxmBasicFieldType.ETH_SRC, false);
		tableWriteSetFields.put(OxmBasicFieldType.ETH_DST, false);
		tableWriteSetFields.put(OxmBasicFieldType.VLAN_VID, false);
		tableWriteSetFields.put(OxmBasicFieldType.VLAN_PCP, false);
		tableFeatures.addProp(TableFeatureFactory.createOxmProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.WRITE_SETFIELD, tableWriteSetFields));

		/*
		 * Set the fields that can be modified via a WRITE instruction for a
		 * miss rule
		 */
		Map<OxmBasicFieldType, Boolean> tableWriteSetFieldsMiss = new HashMap<OxmBasicFieldType, Boolean>();
		tableWriteSetFieldsMiss.put(OxmBasicFieldType.ETH_SRC, false);
		tableWriteSetFieldsMiss.put(OxmBasicFieldType.ETH_DST, false);
		tableWriteSetFieldsMiss.put(OxmBasicFieldType.VLAN_VID, false);
		tableWriteSetFieldsMiss.put(OxmBasicFieldType.VLAN_PCP, false);
		tableFeatures.addProp(TableFeatureFactory.createOxmProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.WRITE_SETFIELD_MISS, tableWriteSetFieldsMiss));

		/* Set the fields that can be modified via a APPLY instruction */
		Map<OxmBasicFieldType, Boolean> tableApplySetFields = new HashMap<OxmBasicFieldType, Boolean>();
		tableApplySetFields.put(OxmBasicFieldType.ETH_SRC, false);
		tableApplySetFields.put(OxmBasicFieldType.ETH_DST, false);
		tableApplySetFields.put(OxmBasicFieldType.VLAN_VID, false);
		tableApplySetFields.put(OxmBasicFieldType.VLAN_PCP, false);
		tableFeatures.addProp(TableFeatureFactory.createOxmProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.APPLY_SETFIELD, tableApplySetFields));

		/*
		 * Set the fields that can be modified via a APPLY instruction for a
		 * miss rule
		 */
		Map<OxmBasicFieldType, Boolean> tableApplySetFieldsMiss = new HashMap<OxmBasicFieldType, Boolean>();
		tableApplySetFieldsMiss.put(OxmBasicFieldType.ETH_SRC, false);
		tableApplySetFieldsMiss.put(OxmBasicFieldType.ETH_DST, false);
		tableApplySetFieldsMiss.put(OxmBasicFieldType.VLAN_VID, false);
		tableApplySetFieldsMiss.put(OxmBasicFieldType.VLAN_PCP, false);
		tableFeatures.addProp(TableFeatureFactory.createOxmProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.APPLY_SETFIELD_MISS, tableApplySetFieldsMiss));

		return (MBodyTableFeatures) tableFeatures.toImmutable();
	}

	public MBodyTableFeatures createMixedTableProperties() {

		/*
		 * Now let us create a Table Features Request message with a new
		 * pipeline and send it to the switch
		 */

		MBodyMutableTableFeatures tableFeatures = new MBodyMutableTableFeatures(ProtocolVersion.V_1_3);

		/* Set the table ID for the new table */
		TableId tblID = TableId.valueOf(7);
		tableFeatures.tableId(tblID);

		/* Set a name for the new table */
		tableFeatures.name("OpenFlow Mixed Match Table");

		/* Set the metadata match & metadata write fields for the table */
		tableFeatures.metadataWrite(0x00);
		tableFeatures.metadataMatch(0x00);

		/* Set the size of the new table */
		tableFeatures.maxEntries(128);

		/* Set the instructions to be supported in the new table */
		Set<InstructionType> tableInstrTypes = new HashSet<InstructionType>();
		tableInstrTypes.add(InstructionType.APPLY_ACTIONS);
		tableInstrTypes.add(InstructionType.WRITE_ACTIONS);
		tableInstrTypes.add(InstructionType.GOTO_TABLE);
		tableInstrTypes.add(InstructionType.METER);

		tableFeatures.addProp(TableFeatureFactory.createInstrProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.INSTRUCTIONS, tableInstrTypes));

		/* Set the instructions to be supported for the table miss rule */
		Set<InstructionType> tableInstrMissTypes = new HashSet<InstructionType>();
		tableInstrMissTypes.add(InstructionType.APPLY_ACTIONS);
		tableInstrMissTypes.add(InstructionType.WRITE_ACTIONS);
		tableInstrMissTypes.add(InstructionType.GOTO_TABLE);
		tableInstrMissTypes.add(InstructionType.METER);

		tableFeatures.addProp(TableFeatureFactory.createInstrProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.INSTRUCTIONS_MISS, tableInstrMissTypes));

		/*
		 * Set the tables to which rules can be pointed to from the current
		 * table
		 */
		Set<TableId> tableNextTables = new HashSet<TableId>();
		tableFeatures.addProp(TableFeatureFactory.createNextTablesProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.NEXT_TABLES, tableNextTables));

		/*
		 * Set the tables to which the table miss rule can be point to from the
		 * current table
		 */
		Set<TableId> tableNextTablesMiss = new HashSet<TableId>();
		tableFeatures.addProp(TableFeatureFactory.createNextTablesProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.NEXT_TABLES_MISS, tableNextTablesMiss));

		/* Set the actions to be supported via WRITE instruction */
		Set<ActionType> tableWriteActions = new HashSet<ActionType>();
		tableWriteActions.add(ActionType.OUTPUT);
		tableWriteActions.add(ActionType.GROUP);
		tableWriteActions.add(ActionType.SET_FIELD);
		tableFeatures.addProp(TableFeatureFactory.createActionProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.WRITE_ACTIONS, tableWriteActions));

		/*
		 * Set the actions to be supported via WRITE instruction for a table
		 * miss rule
		 */
		Set<ActionType> tableWriteActionsMiss = new HashSet<ActionType>();
		tableWriteActionsMiss.add(ActionType.OUTPUT);
		tableWriteActionsMiss.add(ActionType.GROUP);
		tableWriteActionsMiss.add(ActionType.SET_FIELD);
		tableFeatures.addProp(TableFeatureFactory.createActionProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.WRITE_ACTIONS_MISS, tableWriteActionsMiss));

		/* Set the actions to be supported via APPLY instruction */
		Set<ActionType> tableApplyActions = new HashSet<ActionType>();
		tableApplyActions.add(ActionType.OUTPUT);
		tableApplyActions.add(ActionType.GROUP);
		tableApplyActions.add(ActionType.SET_FIELD);
		tableFeatures.addProp(TableFeatureFactory.createActionProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.APPLY_ACTIONS, tableApplyActions));

		/*
		 * Set the actions to be supported via APPLY instruction for a table
		 * miss rule
		 */
		Set<ActionType> tableApplyActionsMiss = new HashSet<ActionType>();
		tableApplyActionsMiss.add(ActionType.OUTPUT);
		tableApplyActionsMiss.add(ActionType.GROUP);
		tableApplyActionsMiss.add(ActionType.SET_FIELD);
		tableFeatures.addProp(TableFeatureFactory.createActionProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.APPLY_ACTIONS_MISS, tableApplyActionsMiss));

		/* Set the fields the table will match on */
		Map<OxmBasicFieldType, Boolean> tableMatchFields = new HashMap<OxmBasicFieldType, Boolean>();
		tableMatchFields.put(OxmBasicFieldType.ETH_TYPE, false);
		tableMatchFields.put(OxmBasicFieldType.IP_PROTO, false);
		tableMatchFields.put(OxmBasicFieldType.ICMPV4_TYPE, false);

		List<MFieldExperimenter> tableExpMatchFields = new ArrayList<MFieldExperimenter>();
		byte[] customData1 = new byte[] { 0x00, 0x02, 0x00, 0x08, 0x00, 0x01 };

		MFieldExperimenter expMatchFieldIPTTL = com.hp.of.lib.match.FieldFactory
				.createExperimenterField(ProtocolVersion.V_1_3, 5, 9345, customData1);

		tableExpMatchFields.add(expMatchFieldIPTTL);

		tableFeatures.addProp(TableFeatureFactory.createOxmProp(ProtocolVersion.V_1_3, TableFeaturePropType.MATCH,
				tableMatchFields, tableExpMatchFields));

		/* Set the match fields that can be wildcarded */
		Map<OxmBasicFieldType, Boolean> tableWildcards = new HashMap<OxmBasicFieldType, Boolean>();
		tableWildcards.put(OxmBasicFieldType.IP_PROTO, false);
		tableWildcards.put(OxmBasicFieldType.ICMPV4_TYPE, false);

		List<MFieldExperimenter> tableExpWildcards = new ArrayList<MFieldExperimenter>();
		MFieldExperimenter expWildcardIPTTL = com.hp.of.lib.match.FieldFactory
				.createExperimenterField(ProtocolVersion.V_1_3, 5, 9345, null);

		tableExpWildcards.add(expWildcardIPTTL);

		tableFeatures.addProp(TableFeatureFactory.createOxmProp(ProtocolVersion.V_1_3, TableFeaturePropType.WILDCARDS,
				tableWildcards, tableExpWildcards));

		/* Set the fields that can be modified via a WRITE instruction */
		Map<OxmBasicFieldType, Boolean> tableWriteSetFields = new HashMap<OxmBasicFieldType, Boolean>();
		tableWriteSetFields.put(OxmBasicFieldType.ETH_SRC, false);
		tableWriteSetFields.put(OxmBasicFieldType.ETH_DST, false);
		tableWriteSetFields.put(OxmBasicFieldType.VLAN_VID, false);
		tableWriteSetFields.put(OxmBasicFieldType.VLAN_PCP, false);
		tableFeatures.addProp(TableFeatureFactory.createOxmProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.WRITE_SETFIELD, tableWriteSetFields));

		/*
		 * Set the fields that can be modified via a WRITE instruction for a
		 * miss rule
		 */
		Map<OxmBasicFieldType, Boolean> tableWriteSetFieldsMiss = new HashMap<OxmBasicFieldType, Boolean>();
		tableWriteSetFieldsMiss.put(OxmBasicFieldType.ETH_SRC, false);
		tableWriteSetFieldsMiss.put(OxmBasicFieldType.ETH_DST, false);
		tableWriteSetFieldsMiss.put(OxmBasicFieldType.VLAN_VID, false);
		tableWriteSetFieldsMiss.put(OxmBasicFieldType.VLAN_PCP, false);
		tableFeatures.addProp(TableFeatureFactory.createOxmProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.WRITE_SETFIELD_MISS, tableWriteSetFieldsMiss));

		/* Set the fields that can be modified via a APPLY instruction */
		Map<OxmBasicFieldType, Boolean> tableApplySetFields = new HashMap<OxmBasicFieldType, Boolean>();
		tableApplySetFields.put(OxmBasicFieldType.ETH_SRC, false);
		tableApplySetFields.put(OxmBasicFieldType.ETH_DST, false);
		tableApplySetFields.put(OxmBasicFieldType.VLAN_VID, false);
		tableApplySetFields.put(OxmBasicFieldType.VLAN_PCP, false);
		tableFeatures.addProp(TableFeatureFactory.createOxmProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.APPLY_SETFIELD, tableApplySetFields));

		/*
		 * Set the fields that can be modified via a APPLY instruction for a
		 * miss rule
		 */
		Map<OxmBasicFieldType, Boolean> tableApplySetFieldsMiss = new HashMap<OxmBasicFieldType, Boolean>();
		tableApplySetFieldsMiss.put(OxmBasicFieldType.ETH_SRC, false);
		tableApplySetFieldsMiss.put(OxmBasicFieldType.ETH_DST, false);
		tableApplySetFieldsMiss.put(OxmBasicFieldType.VLAN_VID, false);
		tableApplySetFieldsMiss.put(OxmBasicFieldType.VLAN_PCP, false);
		tableFeatures.addProp(TableFeatureFactory.createOxmProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.APPLY_SETFIELD_MISS, tableApplySetFieldsMiss));

		return (MBodyTableFeatures) tableFeatures.toImmutable();
	}

	public MBodyTableFeatures hpeSDNTableARPBypass() {

		/*
		 * Now let us create a Table Features Request message with a new
		 * pipeline and send it to the switch
		 */

		MBodyMutableTableFeatures tableFeatures = new MBodyMutableTableFeatures(ProtocolVersion.V_1_3);

		/* Set the table ID for the new table */
		TableId tblID = TableId.valueOf(0);
		tableFeatures.tableId(tblID);

		/* Set a name for the new table */
		tableFeatures.name("ARP Bypass");

		/* Set the metadata match & metadata write fields for the table */
		tableFeatures.metadataWrite(0x00);
		tableFeatures.metadataMatch(0x00);

		/* Set the size of the new table */
		tableFeatures.maxEntries(512);

		/* Set the instructions to be supported in the new table */
		Set<InstructionType> tableInstrTypes = new HashSet<InstructionType>();
		tableInstrTypes.add(InstructionType.APPLY_ACTIONS);
		tableInstrTypes.add(InstructionType.WRITE_ACTIONS);
		tableInstrTypes.add(InstructionType.GOTO_TABLE);
		tableInstrTypes.add(InstructionType.METER);

		tableFeatures.addProp(TableFeatureFactory.createInstrProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.INSTRUCTIONS, tableInstrTypes));

		/* Set the instructions to be supported for the table miss rule */
		Set<InstructionType> tableInstrMissTypes = new HashSet<InstructionType>();
		tableInstrMissTypes.add(InstructionType.APPLY_ACTIONS);
		tableInstrMissTypes.add(InstructionType.WRITE_ACTIONS);
		tableInstrMissTypes.add(InstructionType.GOTO_TABLE);
		tableInstrMissTypes.add(InstructionType.METER);

		tableFeatures.addProp(TableFeatureFactory.createInstrProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.INSTRUCTIONS_MISS, tableInstrMissTypes));

		/*
		 * Set the tables to which rules can be pointed to from the current
		 * table
		 */
		Set<TableId> tableNextTables = new HashSet<TableId>();
		tableNextTables.add(TableId.valueOf(1));
		tableNextTables.add(TableId.valueOf(2));
		tableNextTables.add(TableId.valueOf(3));
		tableNextTables.add(TableId.valueOf(4));
		tableNextTables.add(TableId.valueOf(5));
		tableNextTables.add(TableId.valueOf(6));
		tableNextTables.add(TableId.valueOf(7));
		tableFeatures.addProp(TableFeatureFactory.createNextTablesProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.NEXT_TABLES, tableNextTables));

		/*
		 * Set the tables to which the table miss rule can be point to from the
		 * current table
		 */
		Set<TableId> tableNextTablesMiss = new HashSet<TableId>();
		tableNextTablesMiss.add(TableId.valueOf(1));
		tableNextTablesMiss.add(TableId.valueOf(2));
		tableNextTablesMiss.add(TableId.valueOf(3));
		tableNextTablesMiss.add(TableId.valueOf(4));
		tableNextTablesMiss.add(TableId.valueOf(5));
		tableNextTablesMiss.add(TableId.valueOf(6));
		tableNextTablesMiss.add(TableId.valueOf(7));
		tableFeatures.addProp(TableFeatureFactory.createNextTablesProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.NEXT_TABLES_MISS, tableNextTablesMiss));

		/* Set the actions to be supported via WRITE instruction */
		Set<ActionType> tableWriteActions = new HashSet<ActionType>();
		tableWriteActions.add(ActionType.OUTPUT);
		tableFeatures.addProp(TableFeatureFactory.createActionProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.WRITE_ACTIONS, tableWriteActions));

		/*
		 * Set the actions to be supported via WRITE instruction for a table
		 * miss rule
		 */
		Set<ActionType> tableWriteActionsMiss = new HashSet<ActionType>();
		tableWriteActionsMiss.add(ActionType.OUTPUT);
		tableFeatures.addProp(TableFeatureFactory.createActionProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.WRITE_ACTIONS_MISS, tableWriteActionsMiss));

		/* Set the actions to be supported via APPLY instruction */
		Set<ActionType> tableApplyActions = new HashSet<ActionType>();
		tableApplyActions.add(ActionType.OUTPUT);
		tableFeatures.addProp(TableFeatureFactory.createActionProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.APPLY_ACTIONS, tableApplyActions));

		/*
		 * Set the actions to be supported via APPLY instruction for a table
		 * miss rule
		 */
		Set<ActionType> tableApplyActionsMiss = new HashSet<ActionType>();
		tableApplyActionsMiss.add(ActionType.OUTPUT);
		tableFeatures.addProp(TableFeatureFactory.createActionProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.APPLY_ACTIONS_MISS, tableApplyActionsMiss));

		/* Set the fields the table will match on */
		Map<OxmBasicFieldType, Boolean> tableMatchFields = new HashMap<OxmBasicFieldType, Boolean>();
		tableMatchFields.put(OxmBasicFieldType.IN_PORT, false);
		tableMatchFields.put(OxmBasicFieldType.ETH_TYPE, false);
		tableMatchFields.put(OxmBasicFieldType.ARP_OP, false);

		tableFeatures.addProp(
				TableFeatureFactory.createOxmProp(ProtocolVersion.V_1_3, TableFeaturePropType.MATCH, tableMatchFields));

		return (MBodyTableFeatures) tableFeatures.toImmutable();
	}

	public MBodyTableFeatures hpeSDNTableTopologyLearning() {

		/*
		 * Now let us create a Table Features Request message with a new
		 * pipeline and send it to the switch
		 */

		MBodyMutableTableFeatures tableFeatures = new MBodyMutableTableFeatures(ProtocolVersion.V_1_3);

		/* Set the table ID for the new table */
		TableId tblID = TableId.valueOf(1);
		tableFeatures.tableId(tblID);

		/* Set a name for the new table */
		tableFeatures.name("Topology Learning");

		/* Set the metadata match & metadata write fields for the table */
		tableFeatures.metadataWrite(0x00);
		tableFeatures.metadataMatch(0x00);

		/* Set the size of the new table */
		tableFeatures.maxEntries(4);

		/* Set the instructions to be supported in the new table */
		Set<InstructionType> tableInstrTypes = new HashSet<InstructionType>();
		tableInstrTypes.add(InstructionType.APPLY_ACTIONS);
		tableInstrTypes.add(InstructionType.WRITE_ACTIONS);
		tableInstrTypes.add(InstructionType.GOTO_TABLE);
		tableInstrTypes.add(InstructionType.METER);

		tableFeatures.addProp(TableFeatureFactory.createInstrProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.INSTRUCTIONS, tableInstrTypes));

		/* Set the instructions to be supported for the table miss rule */
		Set<InstructionType> tableInstrMissTypes = new HashSet<InstructionType>();
		tableInstrMissTypes.add(InstructionType.APPLY_ACTIONS);
		tableInstrMissTypes.add(InstructionType.WRITE_ACTIONS);
		tableInstrMissTypes.add(InstructionType.GOTO_TABLE);
		tableInstrMissTypes.add(InstructionType.METER);

		tableFeatures.addProp(TableFeatureFactory.createInstrProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.INSTRUCTIONS_MISS, tableInstrMissTypes));

		/*
		 * Set the tables to which rules can be pointed to from the current
		 * table
		 */
		Set<TableId> tableNextTables = new HashSet<TableId>();
		tableNextTables.add(TableId.valueOf(2));
		tableNextTables.add(TableId.valueOf(3));
		tableNextTables.add(TableId.valueOf(4));
		tableNextTables.add(TableId.valueOf(5));
		tableNextTables.add(TableId.valueOf(6));
		tableNextTables.add(TableId.valueOf(7));
		tableFeatures.addProp(TableFeatureFactory.createNextTablesProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.NEXT_TABLES, tableNextTables));

		/*
		 * Set the tables to which the table miss rule can be point to from the
		 * current table
		 */
		Set<TableId> tableNextTablesMiss = new HashSet<TableId>();
		tableNextTablesMiss.add(TableId.valueOf(2));
		tableNextTablesMiss.add(TableId.valueOf(3));
		tableNextTablesMiss.add(TableId.valueOf(4));
		tableNextTablesMiss.add(TableId.valueOf(5));
		tableNextTablesMiss.add(TableId.valueOf(6));
		tableNextTablesMiss.add(TableId.valueOf(7));
		tableFeatures.addProp(TableFeatureFactory.createNextTablesProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.NEXT_TABLES_MISS, tableNextTablesMiss));

		/* Set the actions to be supported via WRITE instruction */
		Set<ActionType> tableWriteActions = new HashSet<ActionType>();
		tableWriteActions.add(ActionType.OUTPUT);
		tableFeatures.addProp(TableFeatureFactory.createActionProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.WRITE_ACTIONS, tableWriteActions));

		/*
		 * Set the actions to be supported via WRITE instruction for a table
		 * miss rule
		 */
		Set<ActionType> tableWriteActionsMiss = new HashSet<ActionType>();
		tableWriteActionsMiss.add(ActionType.OUTPUT);
		tableFeatures.addProp(TableFeatureFactory.createActionProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.WRITE_ACTIONS_MISS, tableWriteActionsMiss));

		/* Set the actions to be supported via APPLY instruction */
		Set<ActionType> tableApplyActions = new HashSet<ActionType>();
		tableApplyActions.add(ActionType.OUTPUT);
		tableFeatures.addProp(TableFeatureFactory.createActionProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.APPLY_ACTIONS, tableApplyActions));

		/*
		 * Set the actions to be supported via APPLY instruction for a table
		 * miss rule
		 */
		Set<ActionType> tableApplyActionsMiss = new HashSet<ActionType>();
		tableApplyActionsMiss.add(ActionType.OUTPUT);
		tableFeatures.addProp(TableFeatureFactory.createActionProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.APPLY_ACTIONS_MISS, tableApplyActionsMiss));

		/* Set the fields the table will match on */
		Map<OxmBasicFieldType, Boolean> tableMatchFields = new HashMap<OxmBasicFieldType, Boolean>();
		tableMatchFields.put(OxmBasicFieldType.ETH_TYPE, false);
		tableMatchFields.put(OxmBasicFieldType.ARP_OP, false);
		tableMatchFields.put(OxmBasicFieldType.IP_PROTO, false);
		tableMatchFields.put(OxmBasicFieldType.UDP_SRC, false);
		tableMatchFields.put(OxmBasicFieldType.UDP_DST, false);

		tableFeatures.addProp(
				TableFeatureFactory.createOxmProp(ProtocolVersion.V_1_3, TableFeaturePropType.MATCH, tableMatchFields));

		/* Set the match fields that can be wildcarded */
		Map<OxmBasicFieldType, Boolean> tableWildcards = new HashMap<OxmBasicFieldType, Boolean>();
		tableWildcards.put(OxmBasicFieldType.ETH_TYPE, false);
		tableWildcards.put(OxmBasicFieldType.ARP_OP, false);
		tableWildcards.put(OxmBasicFieldType.IP_PROTO, false);
		tableWildcards.put(OxmBasicFieldType.UDP_SRC, false);
		tableWildcards.put(OxmBasicFieldType.UDP_DST, false);
		tableFeatures.addProp(TableFeatureFactory.createOxmProp(ProtocolVersion.V_1_3, TableFeaturePropType.WILDCARDS,
				tableWildcards));

		return (MBodyTableFeatures) tableFeatures.toImmutable();
	}

	public MBodyTableFeatures hpeSDNTableVisibilityCopyTunnel() {

		/*
		 * Now let us create a Table Features Request message with a new
		 * pipeline and send it to the switch
		 */

		MBodyMutableTableFeatures tableFeatures = new MBodyMutableTableFeatures(ProtocolVersion.V_1_3);

		/* Set the table ID for the new table */
		TableId tblID = TableId.valueOf(2);
		tableFeatures.tableId(tblID);

		/* Set a name for the new table */
		tableFeatures.name("Visibility Table");

		/* Set the metadata match & metadata write fields for the table */
		tableFeatures.metadataWrite(0x00);
		tableFeatures.metadataMatch(0x00);

		/* Set the size of the new table */
		tableFeatures.maxEntries(1024);

		/* Set the instructions to be supported in the new table */
		Set<InstructionType> tableInstrTypes = new HashSet<InstructionType>();
		tableInstrTypes.add(InstructionType.APPLY_ACTIONS);
		tableInstrTypes.add(InstructionType.WRITE_ACTIONS);
		tableInstrTypes.add(InstructionType.GOTO_TABLE);
		tableInstrTypes.add(InstructionType.METER);

		tableFeatures.addProp(TableFeatureFactory.createInstrProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.INSTRUCTIONS, tableInstrTypes));

		/* Set the instructions to be supported for the table miss rule */
		Set<InstructionType> tableInstrMissTypes = new HashSet<InstructionType>();
		tableInstrMissTypes.add(InstructionType.APPLY_ACTIONS);
		tableInstrMissTypes.add(InstructionType.WRITE_ACTIONS);
		tableInstrMissTypes.add(InstructionType.GOTO_TABLE);
		tableInstrMissTypes.add(InstructionType.METER);

		tableFeatures.addProp(TableFeatureFactory.createInstrProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.INSTRUCTIONS_MISS, tableInstrMissTypes));

		/*
		 * Set the tables to which rules can be pointed to from the current
		 * table
		 */
		Set<TableId> tableNextTables = new HashSet<TableId>();
		tableNextTables.add(TableId.valueOf(3));
		tableNextTables.add(TableId.valueOf(4));
		tableNextTables.add(TableId.valueOf(5));
		tableNextTables.add(TableId.valueOf(6));
		tableNextTables.add(TableId.valueOf(7));
		tableFeatures.addProp(TableFeatureFactory.createNextTablesProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.NEXT_TABLES, tableNextTables));

		/*
		 * Set the tables to which the table miss rule can be point to from the
		 * current table
		 */
		Set<TableId> tableNextTablesMiss = new HashSet<TableId>();
		tableNextTablesMiss.add(TableId.valueOf(3));
		tableNextTablesMiss.add(TableId.valueOf(4));
		tableNextTablesMiss.add(TableId.valueOf(5));
		tableNextTablesMiss.add(TableId.valueOf(6));
		tableNextTablesMiss.add(TableId.valueOf(7));
		tableFeatures.addProp(TableFeatureFactory.createNextTablesProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.NEXT_TABLES_MISS, tableNextTablesMiss));

		/* Set the actions to be supported via WRITE instruction */
		Set<ActionType> tableWriteActions = new HashSet<ActionType>();
		tableWriteActions.add(ActionType.OUTPUT);
		tableFeatures.addProp(TableFeatureFactory.createActionProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.WRITE_ACTIONS, tableWriteActions));

		/*
		 * Set the actions to be supported via WRITE instruction for a table
		 * miss rule
		 */
		Set<ActionType> tableWriteActionsMiss = new HashSet<ActionType>();
		tableWriteActionsMiss.add(ActionType.OUTPUT);
		tableFeatures.addProp(TableFeatureFactory.createActionProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.WRITE_ACTIONS_MISS, tableWriteActionsMiss));

		/* Set the actions to be supported via APPLY instruction */
		Set<ActionType> tableApplyActions = new HashSet<ActionType>();
		tableApplyActions.add(ActionType.OUTPUT);
		tableFeatures.addProp(TableFeatureFactory.createActionProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.APPLY_ACTIONS, tableApplyActions));

		/*
		 * Set the actions to be supported via APPLY instruction for a table
		 * miss rule
		 */
		Set<ActionType> tableApplyActionsMiss = new HashSet<ActionType>();
		tableApplyActionsMiss.add(ActionType.OUTPUT);
		tableFeatures.addProp(TableFeatureFactory.createActionProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.APPLY_ACTIONS_MISS, tableApplyActionsMiss));

		List<MFieldExperimenter> tableExpMatchFields = new ArrayList<MFieldExperimenter>();
		byte[] ipAddr = new byte[] { 0x00, 0x02, 0x00, 0x0C, 0x00, 0x08 };
		byte[] grePayload = new byte[] { 0x00, 0x03, 0x00, 0x02, 0x00, 0x02 };

		MFieldExperimenter expMatchIPAddr = com.hp.of.lib.match.FieldFactory
				.createExperimenterField(ProtocolVersion.V_1_3, 5, 9345, ipAddr);
		MFieldExperimenter expMatchGREPayload = com.hp.of.lib.match.FieldFactory
				.createExperimenterField(ProtocolVersion.V_1_3, 6, 9345, grePayload);

		tableExpMatchFields.add(expMatchIPAddr);
		tableExpMatchFields.add(expMatchGREPayload);

		/* Set the fields the table will match on */
		Map<OxmBasicFieldType, Boolean> tableMatchFields = new HashMap<OxmBasicFieldType, Boolean>();
		tableMatchFields.put(OxmBasicFieldType.IN_PORT, false);
		tableMatchFields.put(OxmBasicFieldType.ETH_TYPE, false);
		tableMatchFields.put(OxmBasicFieldType.VLAN_VID, false);
		tableMatchFields.put(OxmBasicFieldType.IP_PROTO, false);
		tableFeatures.addProp(TableFeatureFactory.createOxmProp(ProtocolVersion.V_1_3, TableFeaturePropType.MATCH,
				tableMatchFields, tableExpMatchFields));

		List<MFieldExperimenter> tableExpWildcards = new ArrayList<MFieldExperimenter>();
		MFieldExperimenter expWildcardIPAddr = com.hp.of.lib.match.FieldFactory
				.createExperimenterField(ProtocolVersion.V_1_3, 5, 9345, null);
		MFieldExperimenter expWildcardGREPayload = com.hp.of.lib.match.FieldFactory
				.createExperimenterField(ProtocolVersion.V_1_3, 6, 9345, null);

		tableExpWildcards.add(expWildcardIPAddr);
		tableExpWildcards.add(expWildcardGREPayload);

		/* Set the match fields that can be wildcarded */
		Map<OxmBasicFieldType, Boolean> tableWildcards = new HashMap<OxmBasicFieldType, Boolean>();
		tableWildcards.put(OxmBasicFieldType.IN_PORT, false);
		tableWildcards.put(OxmBasicFieldType.ETH_TYPE, false);
		tableWildcards.put(OxmBasicFieldType.VLAN_VID, false);
		tableWildcards.put(OxmBasicFieldType.IP_PROTO, false);
		tableFeatures.addProp(TableFeatureFactory.createOxmProp(ProtocolVersion.V_1_3, TableFeaturePropType.WILDCARDS,
				tableWildcards, tableExpWildcards));

		return (MBodyTableFeatures) tableFeatures.toImmutable();
	}

	public MBodyTableFeatures hpeSDNTableQuarantine() {

		/*
		 * Now let us create a Table Features Request message with a new
		 * pipeline and send it to the switch
		 */

		MBodyMutableTableFeatures tableFeatures = new MBodyMutableTableFeatures(ProtocolVersion.V_1_3);

		/* Set the table ID for the new table */
		TableId tblID = TableId.valueOf(3);
		tableFeatures.tableId(tblID);

		/* Set a name for the new table */
		tableFeatures.name("Quarantine");

		/* Set the metadata match & metadata write fields for the table */
		tableFeatures.metadataWrite(0x00);
		tableFeatures.metadataMatch(0x00);

		/* Set the size of the new table */
		tableFeatures.maxEntries(500);

		/* Set the instructions to be supported in the new table */
		Set<InstructionType> tableInstrTypes = new HashSet<InstructionType>();
		tableInstrTypes.add(InstructionType.APPLY_ACTIONS);
		tableInstrTypes.add(InstructionType.WRITE_ACTIONS);
		tableInstrTypes.add(InstructionType.GOTO_TABLE);
		tableInstrTypes.add(InstructionType.METER);

		tableFeatures.addProp(TableFeatureFactory.createInstrProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.INSTRUCTIONS, tableInstrTypes));

		/* Set the instructions to be supported for the table miss rule */
		Set<InstructionType> tableInstrMissTypes = new HashSet<InstructionType>();
		tableInstrMissTypes.add(InstructionType.APPLY_ACTIONS);
		tableInstrMissTypes.add(InstructionType.WRITE_ACTIONS);
		tableInstrMissTypes.add(InstructionType.GOTO_TABLE);
		tableInstrMissTypes.add(InstructionType.METER);

		tableFeatures.addProp(TableFeatureFactory.createInstrProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.INSTRUCTIONS_MISS, tableInstrMissTypes));

		/*
		 * Set the tables to which rules can be pointed to from the current
		 * table
		 */
		Set<TableId> tableNextTables = new HashSet<TableId>();
		tableNextTables.add(TableId.valueOf(4));
		tableNextTables.add(TableId.valueOf(5));
		tableNextTables.add(TableId.valueOf(6));
		tableNextTables.add(TableId.valueOf(7));
		tableFeatures.addProp(TableFeatureFactory.createNextTablesProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.NEXT_TABLES, tableNextTables));

		/*
		 * Set the tables to which the table miss rule can be point to from the
		 * current table
		 */
		Set<TableId> tableNextTablesMiss = new HashSet<TableId>();
		tableNextTablesMiss.add(TableId.valueOf(4));
		tableNextTablesMiss.add(TableId.valueOf(5));
		tableNextTablesMiss.add(TableId.valueOf(6));
		tableNextTablesMiss.add(TableId.valueOf(7));
		tableFeatures.addProp(TableFeatureFactory.createNextTablesProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.NEXT_TABLES_MISS, tableNextTablesMiss));

		/* Set the actions to be supported via WRITE instruction */
		Set<ActionType> tableWriteActions = new HashSet<ActionType>();
		tableWriteActions.add(ActionType.OUTPUT);
		tableFeatures.addProp(TableFeatureFactory.createActionProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.WRITE_ACTIONS, tableWriteActions));

		/*
		 * Set the actions to be supported via WRITE instruction for a table
		 * miss rule
		 */
		Set<ActionType> tableWriteActionsMiss = new HashSet<ActionType>();
		tableWriteActionsMiss.add(ActionType.OUTPUT);
		tableFeatures.addProp(TableFeatureFactory.createActionProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.WRITE_ACTIONS_MISS, tableWriteActionsMiss));

		/* Set the actions to be supported via APPLY instruction */
		Set<ActionType> tableApplyActions = new HashSet<ActionType>();
		tableApplyActions.add(ActionType.OUTPUT);
		tableFeatures.addProp(TableFeatureFactory.createActionProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.APPLY_ACTIONS, tableApplyActions));

		/*
		 * Set the actions to be supported via APPLY instruction for a table
		 * miss rule
		 */
		Set<ActionType> tableApplyActionsMiss = new HashSet<ActionType>();
		tableApplyActionsMiss.add(ActionType.OUTPUT);
		tableFeatures.addProp(TableFeatureFactory.createActionProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.APPLY_ACTIONS_MISS, tableApplyActionsMiss));

		/* Set the fields the table will match on */
		Map<OxmBasicFieldType, Boolean> tableMatchFields = new HashMap<OxmBasicFieldType, Boolean>();
		tableMatchFields.put(OxmBasicFieldType.ETH_TYPE, false);
		tableMatchFields.put(OxmBasicFieldType.VLAN_VID, false);
		tableMatchFields.put(OxmBasicFieldType.IP_PROTO, false);
		tableMatchFields.put(OxmBasicFieldType.IPV4_SRC, false);
		tableMatchFields.put(OxmBasicFieldType.UDP_DST, false);
		tableFeatures.addProp(
				TableFeatureFactory.createOxmProp(ProtocolVersion.V_1_3, TableFeaturePropType.MATCH, tableMatchFields));

		/* Set the match fields that can be wildcarded */
		Map<OxmBasicFieldType, Boolean> tableWildcards = new HashMap<OxmBasicFieldType, Boolean>();
		tableWildcards.put(OxmBasicFieldType.ETH_TYPE, false);
		tableWildcards.put(OxmBasicFieldType.VLAN_VID, false);
		tableWildcards.put(OxmBasicFieldType.IP_PROTO, false);
		tableWildcards.put(OxmBasicFieldType.IPV4_SRC, false);
		tableWildcards.put(OxmBasicFieldType.UDP_DST, false);
		tableFeatures.addProp(TableFeatureFactory.createOxmProp(ProtocolVersion.V_1_3, TableFeaturePropType.WILDCARDS,
				tableWildcards));

		return (MBodyTableFeatures) tableFeatures.toImmutable();
	}

	public MBodyTableFeatures hpeSDNTableQoS() {

		/*
		 * Now let us create a Table Features Request message with a new
		 * pipeline and send it to the switch
		 */

		MBodyMutableTableFeatures tableFeatures = new MBodyMutableTableFeatures(ProtocolVersion.V_1_3);

		/* Set the table ID for the new table */
		TableId tblID = TableId.valueOf(4);
		tableFeatures.tableId(tblID);

		/* Set a name for the new table */
		tableFeatures.name("QoS");

		/* Set the metadata match & metadata write fields for the table */
		tableFeatures.metadataWrite(0x00);
		tableFeatures.metadataMatch(0x00);

		/* Set the size of the new table */
		tableFeatures.maxEntries(500);

		/* Set the instructions to be supported in the new table */
		Set<InstructionType> tableInstrTypes = new HashSet<InstructionType>();
		tableInstrTypes.add(InstructionType.APPLY_ACTIONS);
		tableInstrTypes.add(InstructionType.WRITE_ACTIONS);
		tableInstrTypes.add(InstructionType.GOTO_TABLE);
		tableInstrTypes.add(InstructionType.METER);

		tableFeatures.addProp(TableFeatureFactory.createInstrProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.INSTRUCTIONS, tableInstrTypes));

		/* Set the instructions to be supported for the table miss rule */
		Set<InstructionType> tableInstrMissTypes = new HashSet<InstructionType>();
		tableInstrMissTypes.add(InstructionType.APPLY_ACTIONS);
		tableInstrMissTypes.add(InstructionType.WRITE_ACTIONS);
		tableInstrMissTypes.add(InstructionType.GOTO_TABLE);
		tableInstrMissTypes.add(InstructionType.METER);

		tableFeatures.addProp(TableFeatureFactory.createInstrProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.INSTRUCTIONS_MISS, tableInstrMissTypes));

		/*
		 * Set the tables to which rules can be pointed to from the current
		 * table
		 */
		Set<TableId> tableNextTables = new HashSet<TableId>();
		tableNextTables.add(TableId.valueOf(5));
		tableNextTables.add(TableId.valueOf(6));
		tableNextTables.add(TableId.valueOf(7));
		tableFeatures.addProp(TableFeatureFactory.createNextTablesProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.NEXT_TABLES, tableNextTables));

		/*
		 * Set the tables to which the table miss rule can be point to from the
		 * current table
		 */
		Set<TableId> tableNextTablesMiss = new HashSet<TableId>();
		tableNextTablesMiss.add(TableId.valueOf(5));
		tableNextTablesMiss.add(TableId.valueOf(6));
		tableNextTablesMiss.add(TableId.valueOf(7));
		tableFeatures.addProp(TableFeatureFactory.createNextTablesProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.NEXT_TABLES_MISS, tableNextTablesMiss));

		/* Set the actions to be supported via WRITE instruction */
		Set<ActionType> tableWriteActions = new HashSet<ActionType>();
		tableWriteActions.add(ActionType.OUTPUT);
		tableWriteActions.add(ActionType.SET_FIELD);
		tableFeatures.addProp(TableFeatureFactory.createActionProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.WRITE_ACTIONS, tableWriteActions));

		/*
		 * Set the actions to be supported via WRITE instruction for a table
		 * miss rule
		 */
		Set<ActionType> tableWriteActionsMiss = new HashSet<ActionType>();
		tableWriteActionsMiss.add(ActionType.OUTPUT);
		tableWriteActionsMiss.add(ActionType.SET_FIELD);
		tableFeatures.addProp(TableFeatureFactory.createActionProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.WRITE_ACTIONS_MISS, tableWriteActionsMiss));

		/* Set the actions to be supported via APPLY instruction */
		Set<ActionType> tableApplyActions = new HashSet<ActionType>();
		tableApplyActions.add(ActionType.OUTPUT);
		tableApplyActions.add(ActionType.SET_FIELD);
		tableFeatures.addProp(TableFeatureFactory.createActionProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.APPLY_ACTIONS, tableApplyActions));

		/*
		 * Set the actions to be supported via APPLY instruction for a table
		 * miss rule
		 */
		Set<ActionType> tableApplyActionsMiss = new HashSet<ActionType>();
		tableApplyActionsMiss.add(ActionType.OUTPUT);
		tableApplyActionsMiss.add(ActionType.SET_FIELD);
		tableFeatures.addProp(TableFeatureFactory.createActionProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.APPLY_ACTIONS_MISS, tableApplyActionsMiss));

		/* Set the fields the table will match on */
		Map<OxmBasicFieldType, Boolean> tableMatchFields = new HashMap<OxmBasicFieldType, Boolean>();
		tableMatchFields.put(OxmBasicFieldType.ETH_TYPE, false);
		tableMatchFields.put(OxmBasicFieldType.VLAN_VID, false);
		tableMatchFields.put(OxmBasicFieldType.IP_PROTO, false);
		tableMatchFields.put(OxmBasicFieldType.IPV4_SRC, false);
		tableMatchFields.put(OxmBasicFieldType.IPV4_DST, false);
		tableMatchFields.put(OxmBasicFieldType.TCP_SRC, false);
		tableMatchFields.put(OxmBasicFieldType.TCP_DST, false);
		tableMatchFields.put(OxmBasicFieldType.UDP_SRC, false);
		tableMatchFields.put(OxmBasicFieldType.UDP_DST, false);
		tableFeatures.addProp(
				TableFeatureFactory.createOxmProp(ProtocolVersion.V_1_3, TableFeaturePropType.MATCH, tableMatchFields));

		/* Set the match fields that can be wildcarded */
		Map<OxmBasicFieldType, Boolean> tableWildcards = new HashMap<OxmBasicFieldType, Boolean>();
		tableWildcards.put(OxmBasicFieldType.ETH_TYPE, false);
		tableWildcards.put(OxmBasicFieldType.VLAN_VID, false);
		tableWildcards.put(OxmBasicFieldType.IP_PROTO, false);
		tableWildcards.put(OxmBasicFieldType.IPV4_SRC, false);
		tableWildcards.put(OxmBasicFieldType.IPV4_DST, false);
		tableWildcards.put(OxmBasicFieldType.TCP_SRC, false);
		tableWildcards.put(OxmBasicFieldType.TCP_DST, false);
		tableWildcards.put(OxmBasicFieldType.UDP_SRC, false);
		tableWildcards.put(OxmBasicFieldType.UDP_DST, false);
		tableFeatures.addProp(TableFeatureFactory.createOxmProp(ProtocolVersion.V_1_3, TableFeaturePropType.WILDCARDS,
				tableWildcards));

		/* Set the fields that can be modified via a WRITE instruction */
		Map<OxmBasicFieldType, Boolean> tableWriteSetFields = new HashMap<OxmBasicFieldType, Boolean>();
		tableWriteSetFields.put(OxmBasicFieldType.VLAN_PCP, false);
		tableWriteSetFields.put(OxmBasicFieldType.IP_DSCP, false);
		tableFeatures.addProp(TableFeatureFactory.createOxmProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.WRITE_SETFIELD, tableWriteSetFields));

		/*
		 * Set the fields that can be modified via a WRITE instruction for a
		 * miss rule
		 */
		Map<OxmBasicFieldType, Boolean> tableWriteSetFieldsMiss = new HashMap<OxmBasicFieldType, Boolean>();
		tableWriteSetFieldsMiss.put(OxmBasicFieldType.VLAN_PCP, false);
		tableWriteSetFieldsMiss.put(OxmBasicFieldType.IP_DSCP, false);
		tableFeatures.addProp(TableFeatureFactory.createOxmProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.WRITE_SETFIELD_MISS, tableWriteSetFieldsMiss));

		/* Set the fields that can be modified via a APPLY instruction */
		Map<OxmBasicFieldType, Boolean> tableApplySetFields = new HashMap<OxmBasicFieldType, Boolean>();
		tableApplySetFields.put(OxmBasicFieldType.VLAN_PCP, false);
		tableApplySetFields.put(OxmBasicFieldType.IP_DSCP, false);
		tableFeatures.addProp(TableFeatureFactory.createOxmProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.APPLY_SETFIELD, tableApplySetFields));

		/*
		 * Set the fields that can be modified via a APPLY instruction for a
		 * miss rule
		 */
		Map<OxmBasicFieldType, Boolean> tableApplySetFieldsMiss = new HashMap<OxmBasicFieldType, Boolean>();
		tableApplySetFieldsMiss.put(OxmBasicFieldType.VLAN_PCP, false);
		tableApplySetFieldsMiss.put(OxmBasicFieldType.IP_DSCP, false);
		tableFeatures.addProp(TableFeatureFactory.createOxmProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.APPLY_SETFIELD_MISS, tableApplySetFieldsMiss));

		return (MBodyTableFeatures) tableFeatures.toImmutable();
	}

	public MBodyTableFeatures hpeSDNTableIPSaaSFirewallOne() {

		/*
		 * Now let us create a Table Features Request message with a new
		 * pipeline and send it to the switch
		 */

		MBodyMutableTableFeatures tableFeatures = new MBodyMutableTableFeatures(ProtocolVersion.V_1_3);

		/* Set the table ID for the new table */
		TableId tblID = TableId.valueOf(5);
		tableFeatures.tableId(tblID);

		/* Set a name for the new table */
		tableFeatures.name("IPSaaS/Firewall - A");

		/* Set the metadata match & metadata write fields for the table */
		tableFeatures.metadataWrite(0x00);
		tableFeatures.metadataMatch(0x00);

		/* Set the size of the new table */
		tableFeatures.maxEntries(4096);

		/* Set the instructions to be supported in the new table */
		Set<InstructionType> tableInstrTypes = new HashSet<InstructionType>();
		tableInstrTypes.add(InstructionType.APPLY_ACTIONS);
		tableInstrTypes.add(InstructionType.WRITE_ACTIONS);
		tableInstrTypes.add(InstructionType.GOTO_TABLE);
		tableInstrTypes.add(InstructionType.METER);

		tableFeatures.addProp(TableFeatureFactory.createInstrProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.INSTRUCTIONS, tableInstrTypes));

		/* Set the instructions to be supported for the table miss rule */
		Set<InstructionType> tableInstrMissTypes = new HashSet<InstructionType>();
		tableInstrMissTypes.add(InstructionType.APPLY_ACTIONS);
		tableInstrMissTypes.add(InstructionType.WRITE_ACTIONS);
		tableInstrMissTypes.add(InstructionType.GOTO_TABLE);
		tableInstrMissTypes.add(InstructionType.METER);

		tableFeatures.addProp(TableFeatureFactory.createInstrProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.INSTRUCTIONS_MISS, tableInstrMissTypes));

		/*
		 * Set the tables to which rules can be pointed to from the current
		 * table
		 */
		Set<TableId> tableNextTables = new HashSet<TableId>();
		tableNextTables.add(TableId.valueOf(6));
		tableNextTables.add(TableId.valueOf(7));
		tableFeatures.addProp(TableFeatureFactory.createNextTablesProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.NEXT_TABLES, tableNextTables));

		/*
		 * Set the tables to which the table miss rule can be point to from the
		 * current table
		 */
		Set<TableId> tableNextTablesMiss = new HashSet<TableId>();
		tableNextTablesMiss.add(TableId.valueOf(6));
		tableNextTablesMiss.add(TableId.valueOf(7));
		tableFeatures.addProp(TableFeatureFactory.createNextTablesProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.NEXT_TABLES_MISS, tableNextTablesMiss));

		/* Set the actions to be supported via WRITE instruction */
		Set<ActionType> tableWriteActions = new HashSet<ActionType>();
		tableWriteActions.add(ActionType.OUTPUT);
		tableFeatures.addProp(TableFeatureFactory.createActionProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.WRITE_ACTIONS, tableWriteActions));

		/*
		 * Set the actions to be supported via WRITE instruction for a table
		 * miss rule
		 */
		Set<ActionType> tableWriteActionsMiss = new HashSet<ActionType>();
		tableWriteActionsMiss.add(ActionType.OUTPUT);
		tableFeatures.addProp(TableFeatureFactory.createActionProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.WRITE_ACTIONS_MISS, tableWriteActionsMiss));

		/* Set the actions to be supported via APPLY instruction */
		Set<ActionType> tableApplyActions = new HashSet<ActionType>();
		tableApplyActions.add(ActionType.OUTPUT);
		tableFeatures.addProp(TableFeatureFactory.createActionProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.APPLY_ACTIONS, tableApplyActions));

		/*
		 * Set the actions to be supported via APPLY instruction for a table
		 * miss rule
		 */
		Set<ActionType> tableApplyActionsMiss = new HashSet<ActionType>();
		tableApplyActionsMiss.add(ActionType.OUTPUT);
		tableFeatures.addProp(TableFeatureFactory.createActionProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.APPLY_ACTIONS_MISS, tableApplyActionsMiss));

		/* Set the fields the table will match on */
		Map<OxmBasicFieldType, Boolean> tableMatchFields = new HashMap<OxmBasicFieldType, Boolean>();
		tableMatchFields.put(OxmBasicFieldType.ETH_TYPE, false);
		tableMatchFields.put(OxmBasicFieldType.VLAN_VID, false);
		tableMatchFields.put(OxmBasicFieldType.IPV4_SRC, false);
		tableFeatures.addProp(
				TableFeatureFactory.createOxmProp(ProtocolVersion.V_1_3, TableFeaturePropType.MATCH, tableMatchFields));

		return (MBodyTableFeatures) tableFeatures.toImmutable();
	}

	public MBodyTableFeatures hpeSDNTableIPSaaSFirewallTwo() {

		/*
		 * Now let us create a Table Features Request message with a new
		 * pipeline and send it to the switch
		 */

		MBodyMutableTableFeatures tableFeatures = new MBodyMutableTableFeatures(ProtocolVersion.V_1_3);

		/* Set the table ID for the new table */
		TableId tblID = TableId.valueOf(6);
		tableFeatures.tableId(tblID);

		/* Set a name for the new table */
		tableFeatures.name("IPSaaS/Firewall - B");

		/* Set the metadata match & metadata write fields for the table */
		tableFeatures.metadataWrite(0x00);
		tableFeatures.metadataMatch(0x00);

		/* Set the size of the new table */
		tableFeatures.maxEntries(4096);

		/* Set the instructions to be supported in the new table */
		Set<InstructionType> tableInstrTypes = new HashSet<InstructionType>();
		tableInstrTypes.add(InstructionType.APPLY_ACTIONS);
		tableInstrTypes.add(InstructionType.WRITE_ACTIONS);
		tableInstrTypes.add(InstructionType.GOTO_TABLE);
		tableInstrTypes.add(InstructionType.METER);

		tableFeatures.addProp(TableFeatureFactory.createInstrProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.INSTRUCTIONS, tableInstrTypes));

		/* Set the instructions to be supported for the table miss rule */
		Set<InstructionType> tableInstrMissTypes = new HashSet<InstructionType>();
		tableInstrMissTypes.add(InstructionType.APPLY_ACTIONS);
		tableInstrMissTypes.add(InstructionType.WRITE_ACTIONS);
		tableInstrMissTypes.add(InstructionType.GOTO_TABLE);
		tableInstrMissTypes.add(InstructionType.METER);

		tableFeatures.addProp(TableFeatureFactory.createInstrProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.INSTRUCTIONS_MISS, tableInstrMissTypes));

		/*
		 * Set the tables to which rules can be pointed to from the current
		 * table
		 */
		Set<TableId> tableNextTables = new HashSet<TableId>();
		tableNextTables.add(TableId.valueOf(7));
		tableFeatures.addProp(TableFeatureFactory.createNextTablesProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.NEXT_TABLES, tableNextTables));

		/*
		 * Set the tables to which the table miss rule can be point to from the
		 * current table
		 */
		Set<TableId> tableNextTablesMiss = new HashSet<TableId>();
		tableNextTablesMiss.add(TableId.valueOf(7));
		tableFeatures.addProp(TableFeatureFactory.createNextTablesProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.NEXT_TABLES_MISS, tableNextTablesMiss));

		/* Set the actions to be supported via WRITE instruction */
		Set<ActionType> tableWriteActions = new HashSet<ActionType>();
		tableWriteActions.add(ActionType.OUTPUT);
		tableFeatures.addProp(TableFeatureFactory.createActionProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.WRITE_ACTIONS, tableWriteActions));

		/*
		 * Set the actions to be supported via WRITE instruction for a table
		 * miss rule
		 */
		Set<ActionType> tableWriteActionsMiss = new HashSet<ActionType>();
		tableWriteActionsMiss.add(ActionType.OUTPUT);
		tableFeatures.addProp(TableFeatureFactory.createActionProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.WRITE_ACTIONS_MISS, tableWriteActionsMiss));

		/* Set the actions to be supported via APPLY instruction */
		Set<ActionType> tableApplyActions = new HashSet<ActionType>();
		tableApplyActions.add(ActionType.OUTPUT);
		tableFeatures.addProp(TableFeatureFactory.createActionProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.APPLY_ACTIONS, tableApplyActions));

		/*
		 * Set the actions to be supported via APPLY instruction for a table
		 * miss rule
		 */
		Set<ActionType> tableApplyActionsMiss = new HashSet<ActionType>();
		tableApplyActionsMiss.add(ActionType.OUTPUT);
		tableFeatures.addProp(TableFeatureFactory.createActionProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.APPLY_ACTIONS_MISS, tableApplyActionsMiss));

		/* Set the fields the table will match on */
		Map<OxmBasicFieldType, Boolean> tableMatchFields = new HashMap<OxmBasicFieldType, Boolean>();
		tableMatchFields.put(OxmBasicFieldType.ETH_TYPE, false);
		tableMatchFields.put(OxmBasicFieldType.VLAN_VID, false);
		tableFeatures.addProp(
				TableFeatureFactory.createOxmProp(ProtocolVersion.V_1_3, TableFeaturePropType.MATCH, tableMatchFields));

		return (MBodyTableFeatures) tableFeatures.toImmutable();
	}

	public MBodyTableFeatures hpeSDNTableHybrid() {

		/*
		 * Now let us create a Table Features Request message with a new
		 * pipeline and send it to the switch
		 */

		MBodyMutableTableFeatures tableFeatures = new MBodyMutableTableFeatures(ProtocolVersion.V_1_3);

		/* Set the table ID for the new table */
		TableId tblID = TableId.valueOf(7);
		tableFeatures.tableId(tblID);

		/* Set a name for the new table */
		tableFeatures.name("Hybrid");

		/* Set the metadata match & metadata write fields for the table */
		tableFeatures.metadataWrite(0x00);
		tableFeatures.metadataMatch(0x00);

		/* Set the size of the new table */
		tableFeatures.maxEntries(2);

		/* Set the instructions to be supported in the new table */
		Set<InstructionType> tableInstrTypes = new HashSet<InstructionType>();
		tableInstrTypes.add(InstructionType.APPLY_ACTIONS);
		tableInstrTypes.add(InstructionType.WRITE_ACTIONS);
		tableInstrTypes.add(InstructionType.METER);

		tableFeatures.addProp(TableFeatureFactory.createInstrProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.INSTRUCTIONS, tableInstrTypes));

		/* Set the instructions to be supported for the table miss rule */
		Set<InstructionType> tableInstrMissTypes = new HashSet<InstructionType>();
		tableInstrMissTypes.add(InstructionType.APPLY_ACTIONS);
		tableInstrMissTypes.add(InstructionType.WRITE_ACTIONS);
		tableInstrMissTypes.add(InstructionType.METER);

		tableFeatures.addProp(TableFeatureFactory.createInstrProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.INSTRUCTIONS_MISS, tableInstrMissTypes));


		/* Set the actions to be supported via WRITE instruction */
		Set<ActionType> tableWriteActions = new HashSet<ActionType>();
		tableWriteActions.add(ActionType.OUTPUT);
		tableFeatures.addProp(TableFeatureFactory.createActionProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.WRITE_ACTIONS, tableWriteActions));

		/*
		 * Set the actions to be supported via WRITE instruction for a table
		 * miss rule
		 */
		Set<ActionType> tableWriteActionsMiss = new HashSet<ActionType>();
		tableWriteActionsMiss.add(ActionType.OUTPUT);
		tableFeatures.addProp(TableFeatureFactory.createActionProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.WRITE_ACTIONS_MISS, tableWriteActionsMiss));

		/* Set the actions to be supported via APPLY instruction */
		Set<ActionType> tableApplyActions = new HashSet<ActionType>();
		tableApplyActions.add(ActionType.OUTPUT);
		tableFeatures.addProp(TableFeatureFactory.createActionProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.APPLY_ACTIONS, tableApplyActions));

		/*
		 * Set the actions to be supported via APPLY instruction for a table
		 * miss rule
		 */
		Set<ActionType> tableApplyActionsMiss = new HashSet<ActionType>();
		tableApplyActionsMiss.add(ActionType.OUTPUT);
		tableFeatures.addProp(TableFeatureFactory.createActionProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.APPLY_ACTIONS_MISS, tableApplyActionsMiss));

		/* Set the fields the table will match on */
		Map<OxmBasicFieldType, Boolean> tableMatchFields = new HashMap<OxmBasicFieldType, Boolean>();
		tableMatchFields.put(OxmBasicFieldType.VLAN_VID, false);
		tableFeatures.addProp(
				TableFeatureFactory.createOxmProp(ProtocolVersion.V_1_3, TableFeaturePropType.MATCH, tableMatchFields));

		/* Set the match fields that can be wildcarded */
		Map<OxmBasicFieldType, Boolean> tableWildcards = new HashMap<OxmBasicFieldType, Boolean>();
		tableWildcards.put(OxmBasicFieldType.VLAN_VID, false);
		tableFeatures.addProp(TableFeatureFactory.createOxmProp(ProtocolVersion.V_1_3, TableFeaturePropType.WILDCARDS,
				tableWildcards));
		
		return (MBodyTableFeatures) tableFeatures.toImmutable();
	}
	
	public void cbsGenerateFlowMod() {
		// Set a random priority to the flow
		int prio = 100;
		long cookie = 200;
		byte[] customData = new byte[] { 0x00, 0x00, 0x00, 0x04, 0x01, 0x01, 0x01, 0x01 };

		MutableMatch matchField = createMatch(ProtocolVersion.V_1_3);

		matchField.addField(
				createBasicField(ProtocolVersion.V_1_3, OxmBasicFieldType.ETH_TYPE, EthernetType.valueOf(0x800)));

		matchField.addField(
				com.hp.of.lib.match.FieldFactory.createExperimenterField(ProtocolVersion.V_1_3, 5, 9345, customData));

		OfmMutableFlowMod flow = (OfmMutableFlowMod) com.hp.of.lib.msg.MessageFactory.create(ProtocolVersion.V_1_3,
				MessageType.FLOW_MOD, FlowModCommand.ADD);

		Set<FlowModFlag> FLAGS = EnumSet.of(FlowModFlag.SEND_FLOW_REM, FlowModFlag.RESET_COUNTS);

		flow.idleTimeout(0).hardTimeout(0).bufferId(BufferId.NO_BUFFER).tableId(TableId.valueOf(0)).cookie(cookie)
				.priority(prio).match((Match) matchField.toImmutable()).flowModFlags(FLAGS);

		InstrMutableAction action = createMutableInstruction(ProtocolVersion.V_1_3, InstructionType.APPLY_ACTIONS);
		action.addAction(createAction(ProtocolVersion.V_1_3, ActionType.OUTPUT, 2));

		// flow.addInstruction((Instruction) action.toImmutable());

		/* Push the Flow to the DataPath if required */

		try {
			MessageFuture status = controller.sendConfirmedFlowMod((OfmFlowMod) flow.toImmutable(), datapathId);
			status.await();

		} catch (OpenflowException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		return;
	}
}
