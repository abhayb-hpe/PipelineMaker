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

			tableFeatureRequestBody.addTableFeatures(hpeSDNTableTopo1());
			tableFeatureRequestBody.addTableFeatures(hpeSDNTableTopo2());
			tableFeatureRequestBody.addTableFeatures(hpeSDNTableVisibility());
			tableFeatureRequestBody.addTableFeatures(hpeSDNTableQuarantine());
			tableFeatureRequestBody.addTableFeatures(hpeSDNTableQoS());
			tableFeatureRequestBody.addTableFeatures(hpeSDNTableIPSFirewall1());
			tableFeatureRequestBody.addTableFeatures(hpeSDNTableIPSFirewall2());
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

		} catch (OpenflowException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}

		log.info("PipelineInfo Collected for DataPath: " + datapathId.toString());

	}

	public MBodyTableFeatures hpeSDNTableTopo1() {

		/*
		 * Now let us create a Table Features Request message with a new
		 * pipeline and send it to the switch
		 */

		MBodyMutableTableFeatures tableFeatures = new MBodyMutableTableFeatures(ProtocolVersion.V_1_3);

		/* Set the table ID for the new table */
		TableId tblID = TableId.valueOf(0);
		tableFeatures.tableId(tblID);

		/* Set a name for the new table */
		tableFeatures.name("Topology 1");

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
		tableFeatures.addProp(TableFeatureFactory.createNextTablesProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.NEXT_TABLES, tableNextTables));

		/*
		 * Set the tables to which the table miss rule can be point to from the
		 * current table
		 */
		Set<TableId> tableNextTablesMiss = new HashSet<TableId>();
		tableNextTablesMiss.add(TableId.valueOf(1));
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

		tableFeatures.addProp(
				TableFeatureFactory.createOxmProp(ProtocolVersion.V_1_3, TableFeaturePropType.MATCH, tableMatchFields));

		return (MBodyTableFeatures) tableFeatures.toImmutable();
	}

	public MBodyTableFeatures hpeSDNTableTopo2() {

		/*
		 * Now let us create a Table Features Request message with a new
		 * pipeline and send it to the switch
		 */

		MBodyMutableTableFeatures tableFeatures = new MBodyMutableTableFeatures(ProtocolVersion.V_1_3);

		/* Set the table ID for the new table */
		TableId tblID = TableId.valueOf(1);
		tableFeatures.tableId(tblID);

		/* Set a name for the new table */
		tableFeatures.name("Topology 2");

		/* Set the metadata match & metadata write fields for the table */
		tableFeatures.metadataWrite(0x00);
		tableFeatures.metadataMatch(0x00);

		/* Set the size of the new table */
		tableFeatures.maxEntries(16);

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
		tableFeatures.addProp(TableFeatureFactory.createNextTablesProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.NEXT_TABLES, tableNextTables));

		/*
		 * Set the tables to which the table miss rule can be point to from the
		 * current table
		 */
		Set<TableId> tableNextTablesMiss = new HashSet<TableId>();
		tableNextTablesMiss.add(TableId.valueOf(2));
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
		tableMatchFields.put(OxmBasicFieldType.ICMPV6_TYPE, false);

		tableFeatures.addProp(
				TableFeatureFactory.createOxmProp(ProtocolVersion.V_1_3, TableFeaturePropType.MATCH, tableMatchFields));

		/* Set the match fields that can be wildcarded */
		Map<OxmBasicFieldType, Boolean> tableWildcards = new HashMap<OxmBasicFieldType, Boolean>();
		tableWildcards.put(OxmBasicFieldType.ETH_TYPE, false);
		tableWildcards.put(OxmBasicFieldType.ARP_OP, false);
		tableWildcards.put(OxmBasicFieldType.IP_PROTO, false);
		tableWildcards.put(OxmBasicFieldType.UDP_SRC, false);
		tableWildcards.put(OxmBasicFieldType.UDP_DST, false);
		tableWildcards.put(OxmBasicFieldType.ICMPV6_TYPE, false);
		tableFeatures.addProp(TableFeatureFactory.createOxmProp(ProtocolVersion.V_1_3, TableFeaturePropType.WILDCARDS,
				tableWildcards));

		return (MBodyTableFeatures) tableFeatures.toImmutable();
	}

	public MBodyTableFeatures hpeSDNTableVisibility() {

		/*
		 * Now let us create a Table Features Request message with a new
		 * pipeline and send it to the switch
		 */

		MBodyMutableTableFeatures tableFeatures = new MBodyMutableTableFeatures(ProtocolVersion.V_1_3);

		/* Set the table ID for the new table */
		TableId tblID = TableId.valueOf(2);
		tableFeatures.tableId(tblID);

		/* Set a name for the new table */
		tableFeatures.name("Visibility");

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
		tableNextTables.add(TableId.valueOf(3));
		tableFeatures.addProp(TableFeatureFactory.createNextTablesProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.NEXT_TABLES, tableNextTables));

		/*
		 * Set the tables to which the table miss rule can be point to from the
		 * current table
		 */
		Set<TableId> tableNextTablesMiss = new HashSet<TableId>();
		tableNextTablesMiss.add(TableId.valueOf(3));
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

		MFieldExperimenter expMatchTcpFlags = com.hp.of.lib.match.FieldFactory
				.createExperimenterField(ProtocolVersion.V_1_3, 4, 9345, null);
		MFieldExperimenter expMatchIPAddr = com.hp.of.lib.match.FieldFactory
				.createExperimenterField(ProtocolVersion.V_1_3, 5, 9345, ipAddr);

		tableExpMatchFields.add(expMatchTcpFlags);
		tableExpMatchFields.add(expMatchIPAddr);

		/* Set the fields the table will match on */
		Map<OxmBasicFieldType, Boolean> tableMatchFields = new HashMap<OxmBasicFieldType, Boolean>();
		tableMatchFields.put(OxmBasicFieldType.IN_PORT, false);
		tableMatchFields.put(OxmBasicFieldType.ETH_TYPE, false);
		tableMatchFields.put(OxmBasicFieldType.VLAN_VID, false);
		tableMatchFields.put(OxmBasicFieldType.IP_PROTO, false);
		tableMatchFields.put(OxmBasicFieldType.TCP_SRC, false);
		tableMatchFields.put(OxmBasicFieldType.TCP_DST, false);
		tableMatchFields.put(OxmBasicFieldType.UDP_SRC, false);
		tableMatchFields.put(OxmBasicFieldType.UDP_DST, false);
		tableFeatures.addProp(TableFeatureFactory.createOxmProp(ProtocolVersion.V_1_3, TableFeaturePropType.MATCH,
				tableMatchFields, tableExpMatchFields));

		List<MFieldExperimenter> tableExpWildcards = new ArrayList<MFieldExperimenter>();
		MFieldExperimenter expWildcardTCPFlags = com.hp.of.lib.match.FieldFactory
				.createExperimenterField(ProtocolVersion.V_1_3, 4, 9345, null);
		MFieldExperimenter expWildcardIPAddr = com.hp.of.lib.match.FieldFactory
				.createExperimenterField(ProtocolVersion.V_1_3, 5, 9345, null);

		tableExpWildcards.add(expWildcardIPAddr);
		tableExpWildcards.add(expWildcardTCPFlags);

		/* Set the match fields that can be wildcarded */
		Map<OxmBasicFieldType, Boolean> tableWildcards = new HashMap<OxmBasicFieldType, Boolean>();
		tableWildcards.put(OxmBasicFieldType.IN_PORT, false);
		tableWildcards.put(OxmBasicFieldType.ETH_TYPE, false);
		tableWildcards.put(OxmBasicFieldType.VLAN_VID, false);
		tableWildcards.put(OxmBasicFieldType.IP_PROTO, false);
		tableWildcards.put(OxmBasicFieldType.TCP_SRC, false);
		tableWildcards.put(OxmBasicFieldType.TCP_DST, false);
		tableWildcards.put(OxmBasicFieldType.UDP_SRC, false);
		tableWildcards.put(OxmBasicFieldType.UDP_DST, false);
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
		tableFeatures.addProp(TableFeatureFactory.createNextTablesProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.NEXT_TABLES, tableNextTables));

		/*
		 * Set the tables to which the table miss rule can be point to from the
		 * current table
		 */
		Set<TableId> tableNextTablesMiss = new HashSet<TableId>();
		tableNextTablesMiss.add(TableId.valueOf(4));
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
		tableMatchFields.put(OxmBasicFieldType.IPV4_SRC, true);
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
		tableFeatures.addProp(TableFeatureFactory.createNextTablesProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.NEXT_TABLES, tableNextTables));

		/*
		 * Set the tables to which the table miss rule can be point to from the
		 * current table
		 */
		Set<TableId> tableNextTablesMiss = new HashSet<TableId>();
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
		tableMatchFields.put(OxmBasicFieldType.IP_PROTO, false);
		tableMatchFields.put(OxmBasicFieldType.IPV4_SRC, true);
		tableMatchFields.put(OxmBasicFieldType.IPV4_DST, true);
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

	public MBodyTableFeatures hpeSDNTableIPSFirewall1() {

		/*
		 * Now let us create a Table Features Request message with a new
		 * pipeline and send it to the switch
		 */

		MBodyMutableTableFeatures tableFeatures = new MBodyMutableTableFeatures(ProtocolVersion.V_1_3);

		/* Set the table ID for the new table */
		TableId tblID = TableId.valueOf(5);
		tableFeatures.tableId(tblID);

		/* Set a name for the new table */
		tableFeatures.name("IPS/Firewall 1");

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
		tableFeatures.addProp(TableFeatureFactory.createNextTablesProp(ProtocolVersion.V_1_3,
				TableFeaturePropType.NEXT_TABLES, tableNextTables));

		/*
		 * Set the tables to which the table miss rule can be point to from the
		 * current table
		 */
		Set<TableId> tableNextTablesMiss = new HashSet<TableId>();
		tableNextTablesMiss.add(TableId.valueOf(6));
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

	public MBodyTableFeatures hpeSDNTableIPSFirewall2() {

		/*
		 * Now let us create a Table Features Request message with a new
		 * pipeline and send it to the switch
		 */

		MBodyMutableTableFeatures tableFeatures = new MBodyMutableTableFeatures(ProtocolVersion.V_1_3);

		/* Set the table ID for the new table */
		TableId tblID = TableId.valueOf(6);
		tableFeatures.tableId(tblID);

		/* Set a name for the new table */
		tableFeatures.name("IPS/Firewall 2");

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

}
