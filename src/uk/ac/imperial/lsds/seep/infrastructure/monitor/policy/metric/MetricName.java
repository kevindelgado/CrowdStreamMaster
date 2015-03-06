/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package uk.ac.imperial.lsds.seep.infrastructure.monitor.policy.metric;

/**
 * Enumeration to 
 * @author mrouaux
 */
public enum MetricName {
    
    DEFAULT("default"),
    CPU_UTILIZATION("cpu"),
    MEM_UTILIZATION("mem"),
    HEAP_SIZE("memory"),
    HEAP_UTILIZATION("memory-usage"),
    QUEUE_LENGTH("queue-length"),
    OPERATOR_LATENCY("latency"),
    BATTERY_LIFE("battery"),
    WIFI_STRENGTH("wifi"),
    PROCESS_TIME("process-time"),
    RECOGNIZE_TIME("recognize-time"),
    TRANSLATE_TIME("translate-time"),
    NETWORK_TIME("network-time");
    

    public static MetricName metric(String name) {
        return MetricName.fromValue(name);
    }
    
    private static MetricName[] values = MetricName.values();
    private String name;
    
    MetricName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public static MetricName fromValue(String name) {
        for(MetricName metric : values) {
            if(metric.getName().equals(name)) {
                return metric; 
            }
        }
    
        return null;
    }
    
    public static String toString(MetricName metric) {
        return metric.getName();
    }

    @Override
    public String toString() {
        return "MetricName{" + "name=" + name + '}';
    }
}
