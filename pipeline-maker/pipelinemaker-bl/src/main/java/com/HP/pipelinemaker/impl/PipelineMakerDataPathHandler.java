//  (c) Copyright 2013 Hewlett-Packard Development Company, L.P.

package com.HP.pipelinemaker.impl ;

import java.util.ArrayList ;
import java.util.Collection;
import java.util.HashMap ;
import java.util.Map ;
import java.util.Set ;
import java.util.UUID;

import org.apache.felix.scr.annotations.Activate ;
import org.apache.felix.scr.annotations.Component ;
import org.apache.felix.scr.annotations.Deactivate ;
import org.apache.felix.scr.annotations.Reference ;
import org.apache.felix.scr.annotations.ReferenceCardinality ;
import org.apache.felix.scr.annotations.ReferencePolicy ;
import org.apache.felix.scr.annotations.Service ;
import org.slf4j.Logger ;

import com.hp.api.Id;
import com.hp.of.ctl.ControllerService ;
import com.hp.of.ctl.DataPathEvent ;
import com.hp.of.ctl.DataPathListener ;
import com.hp.of.ctl.OpenflowEventType ;
import com.hp.of.ctl.QueueEvent ;
import com.hp.of.lib.ProtocolVersion ;
import com.hp.of.lib.dt.DataPathId ;
import com.hp.of.lib.dt.DataPathInfo ;
import com.HP.pipelinemaker.api.PipelineMakerService;
import com.HP.pipelinemaker.model.PipelineMaker;
import com.hp.sdn.adm.alert.AlertService ;
import com.hp.sdn.misc.SdnLog ;
import com.hp.util.event.EventDispatchService ;

/**
 * Sample TableCapabilityExplorer service implementation.
 */
@Component(metatype = true)
@Service
public class PipelineMakerDataPathHandler implements PipelineMakerService {

   @Reference(
         name = "ControllerService",
         cardinality = ReferenceCardinality.OPTIONAL_UNARY,
         policy = ReferencePolicy.DYNAMIC)
   volatile ControllerService             controller ;

   @Reference(
         name = "AlertService",
         cardinality = ReferenceCardinality.OPTIONAL_UNARY,
         policy = ReferencePolicy.DYNAMIC)
   volatile AlertService                  alertService ;

   @Reference(
         name = "EventDispatchService",
         referenceInterface = EventDispatchService.class,
         policy = ReferencePolicy.DYNAMIC,
         cardinality = ReferenceCardinality.OPTIONAL_UNARY)
   private volatile EventDispatchService  dispatcher ;

   private final ColumbusDataPathListener columbusDataPathListener = new ColumbusDataPathListener() ;

   ArrayList<DataPathId>                  ColumbusDataPaths        = new ArrayList<DataPathId>() ;

   HashMap<DataPathId, PipelineMakerManager>        columbusDBList           = new HashMap<DataPathId, PipelineMakerManager>() ;

   private static final Logger            log                      = SdnLog.APP
                                                                         .getLogger() ;

   /**
    * Activates the app.
    * 
    * @param properties
    *           config properties
    */
   @Activate
   void activate(Map<String, Object> properties) {
      log.info("PipelineMaker has started") ;
      // parseProps(properties) ;
      pipelinemakerAddExistingDataPaths() ;
      controller.addDataPathListener(columbusDataPathListener) ;
   }

   @Deactivate
   void deactivate() {
      if (controller != null) {
         controller.removeDataPathListener(columbusDataPathListener) ;
      }
   }

   @Override
   public boolean pipelinemakerAddExistingDataPaths() {

      Set<DataPathInfo> datapathInfoSet = controller.getAllDataPathInfo() ;

      for (DataPathInfo datapathInfo : datapathInfoSet) {
         pipelinemakerAddDataPath(datapathInfo.dpid()) ;
      }

      return true ;
   }

   @Override
   public boolean pipelinemakerAddDataPath(DataPathId datapath) {

      if (!ColumbusDataPaths.contains(datapath)) {
         log.info("Registering DataPath " + datapath + " with PipelineMaker");
         ColumbusDataPaths.add(datapath) ;
         pipelinemakerGetTableFeaturesForDataPath(datapath) ;
      }
      
      return true ;
   }

   @Override
   public boolean pipelinemakerRemoveDataPath(DataPathId datapath) {

      if (ColumbusDataPaths.contains(datapath)) {
         log.info("De-Registering DataPath " + datapath + " with PipelineMaker");
         ColumbusDataPaths.remove(datapath) ;
      }

      return true ;
   }

   @Override
   public boolean pipelinemakerGetTableFeaturesForDataPath(DataPathId datapath) {

      PipelineMakerManager pipelineMakerCore = new PipelineMakerManager(datapath) ;
      pipelineMakerCore.setControllerService(controller);
      columbusDBList.put(datapath, pipelineMakerCore) ;
      pipelineMakerCore.start();
      
      return true ;
   }

   class ColumbusDataPathListener implements DataPathListener {

      @Override
      public void event(DataPathEvent dpEvent) {

         /*
          * NOTE: If a new DataPath is added to the network, add it to active
          * list of PipelineMaker Datapaths.
          * Modify the pipeline for the new added datapath.
          */
         if ((dpEvent.type() == OpenflowEventType.DATAPATH_CONNECTED)
               && (dpEvent.negotiated().ge(ProtocolVersion.V_1_3))) {
            pipelinemakerAddDataPath(dpEvent.dpid()) ;
         }
         else if ((dpEvent.type() == OpenflowEventType.DATAPATH_DISCONNECTED)
               && (dpEvent.negotiated().ge(ProtocolVersion.V_1_3))) {
            pipelinemakerRemoveDataPath(dpEvent.dpid()) ;
         }

      }

      @Override
      public void queueEvent(QueueEvent queueEvent) {

         return ;
      }

   }

@Override
public Collection<PipelineMaker> getAll() {
	// TODO Auto-generated method stub
	return null;
}

@Override
public PipelineMaker create(String name) {
	// TODO Auto-generated method stub
	return null;
}

@Override
public PipelineMaker get(Id<PipelineMaker, UUID> id) {
	// TODO Auto-generated method stub
	return null;
}

@Override
public void delete(Id<PipelineMaker, UUID> id) {
	// TODO Auto-generated method stub
	
}

}
