package com.example.query;
/*******************************************************************************
 * Copyright (c) 2014 Imperial College London
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Raul Castro Fernandez - initial API and implementation
 ******************************************************************************/
import java.util.ArrayList;

import uk.ac.imperial.lsds.seep.api.QueryBuilder;
import uk.ac.imperial.lsds.seep.api.QueryComposer;
import uk.ac.imperial.lsds.seep.api.QueryPlan;
import uk.ac.imperial.lsds.seep.comm.NodeManagerCommunication;
import uk.ac.imperial.lsds.seep.operator.Connectable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.android_seep_master.FaceTask;
public class Base implements QueryComposer{
	Logger LOG = LoggerFactory.getLogger(Base.class);

//	int numDetectors = 3;
//	int numRecognizers = 2;
//	
//	ArrayList<Connectable> detectors = new ArrayList<Connectable>(); 
//    ArrayList<Connectable> recognizers = new ArrayList<Connectable>();
	ArrayList<Connectable> processors = new ArrayList<Connectable>();

	public QueryPlan compose() {


		/** Declare operators **/

		// Declare Source
		ArrayList<String> srcFields = new ArrayList<String>();
		srcFields.add("value0");
		srcFields.add("value1");
		srcFields.add("value2");
		srcFields.add("value3");
		srcFields.add("value4");
		srcFields.add("value5");
		srcFields.add("value6");
		srcFields.add("value7");
		srcFields.add("value8");
		srcFields.add("value9");
		srcFields.add("value10");
		srcFields.add("value11");
		srcFields.add("value12");

		Connectable src = QueryBuilder.newStatelessSource(new Source(), 0, srcFields);

		// Declare processor1
		ArrayList<String> pFields = new ArrayList<String>();
		pFields.add("value0");
		pFields.add("value1");
		pFields.add("value2");
		pFields.add("value3");
		pFields.add("value4");
		pFields.add("value5");
		pFields.add("value6");
		pFields.add("value7");
		pFields.add("value8");
		pFields.add("value9");
		pFields.add("value10");
		pFields.add("value11");
		pFields.add("value12");

		//Connectable detector = QueryBuilder.newStatelessOperator(new Detector(), 1, pFields);
		//Connectable detector2 = QueryBuilder.newStatelessOperator(new Detector(), -1, pFields);

		//Connectable recognizer = QueryBuilder.newStatelessOperator(new Recognizer(), 2, pFields);

//		for (int i = 0; i < numDetectors; i++){
//			detectors.add(QueryBuilder.newStatelessOperator(new Detector(), i*2+1, pFields));
//		}
//		
//		for (int i = 0; i < numRecognizers; i++){
//			recognizers.add(QueryBuilder.newStatelessOperator(new Recognizer(), (i+1)*2, pFields));
//		}
		
		for (int i = 0; i < FaceTask.numOps; i++){
//			detectors.add(QueryBuilder.newStatelessOperator(new Detector(), i*2+1, pFields));
//			recognizers.add(QueryBuilder.newStatelessOperator(new Recognizer(), (i+1)*2, pFields));
			processors.add(QueryBuilder.newStatelessOperator(new ProcessorUnited(), i+1, pFields));
		}

		// Declare sink
		ArrayList<String> snkFields = new ArrayList<String>();
		snkFields.add("value0");
		snkFields.add("value1");		
		snkFields.add("value2");
		snkFields.add("value3");
		snkFields.add("value4");
		snkFields.add("value5");
		snkFields.add("value6");
		snkFields.add("value7");
		snkFields.add("value8");
		snkFields.add("value9");
		snkFields.add("value10");
		snkFields.add("value11");
		snkFields.add("value12");

		Connectable snk = QueryBuilder.newStatelessSink(new Sink(), 100, snkFields);

		/** Connect operators **/
		//src.connectTo(p, true, 0);	
		//p.connectTo(snk, true, 0);	
		
//		src.connectTo(detector, true, 0);
//		detector.connectTo(recognizer, true, 0);
//		src.connectTo(detector2, true, 0);
//		detector2.connectTo(recognizer, true, 0);
//		recognizer.connectTo(snk, true, 0);
		
		for(int i = 0; i < FaceTask.numOps; i++){
//			src.connectTo(detectors.get(i), true, 0);
//			for (int j = 0; j < numRecognizers; j++){
//				detectors.get(i).connectTo(recognizers.get(j), true, 0);
//			}
//			detectors.get(i).connectTo(recognizers.get(i), true, 0);
//			recognizers.get(i).connectTo(snk, true, 0);
			src.connectTo(processors.get(i), true);
			processors.get(i).connectTo(snk, true);
		}	
//		for (int j = 0; j < numRecognizers; j++){
//		}

		LOG.info(">>>>>>>>>>>>>>>>>>>>From Base<<<<<<<<<<<<<<<<<<<<<<<<<");

		return QueryBuilder.build();
	}
}
