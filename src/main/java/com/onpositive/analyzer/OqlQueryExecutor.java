package com.onpositive.analyzer;

import com.onpositive.analyzer.printing.InstancePrinter;
import org.netbeans.lib.profiler.heap.*;
import org.netbeans.modules.profiler.oql.engine.api.OQLEngine;

import javax.script.ScriptEngine;
import java.lang.reflect.Field;

public class OqlQueryExecutor {

    private final Heap heap;
    private OQLEngine oqlEngine;

    public OqlQueryExecutor(Heap heap) {
        this.heap = heap;
    }

    public String executeOql(String query, int maxResults) throws Exception {
        if (oqlEngine == null) {
            oqlEngine = new OQLEngine(heap);
            patchOqlEngineForFastString(oqlEngine);
            heap.getJavaClassByName("java.lang.String");
            try {
                heap.getJavaClassByName("java.lang.StringUTF16");
            } catch (Exception ignored) {
            }
        }

        StringBuilder resultBuilder = new StringBuilder();
        InstancePrinter printer = new InstancePrinter();

        oqlEngine.executeQuery(query, new OQLEngine.ObjectVisitor() {
            int count = 0;

            @Override
            public boolean visit(Object o) {
                count++;
                if (count > maxResults) {
                    return false;
                }

                if (o instanceof Instance) {
                    resultBuilder.append(String.format("[%d] \"%s\"\n", count, printer.print(o)));
                } else if (o != null) {
                    resultBuilder.append(String.format("[%d] %s\n", count, o));
                }
                return true;
            }
        });

        if (resultBuilder.isEmpty()) {
            return "No results found or empty result set.";
        }

        return "Query Results:\n" + resultBuilder.toString();
    }

    private void patchOqlEngineForFastString(OQLEngine oqlEngine) {
        try {
            Field delegateField = OQLEngine.class.getDeclaredField("delegate");
            delegateField.setAccessible(true);
            Object delegate = delegateField.get(oqlEngine);

            Field engineField = delegate.getClass().getDeclaredField("engine");
            engineField.setAccessible(true);
            ScriptEngine scriptEngine = (ScriptEngine) engineField.get(delegate);

            String jsPatch =
                "var FastStr = Java.type(\"com.onpositive.analyzer.util.ValueUtil\");" +
                "JavaObjectWrapper = function(instance) {" +
                "  var things = instance.fieldValues;" +
                "  var fldValueCache = new Array();" +
                "  return new JSAdapter() {" +
                "    __getIds__ : function() {" +
                "      var res = new Array(things.size());" +
                "      for(var j=0;j<things.size();j++) { res[j] = things.get(j).field.name; }" +
                "      return res;" +
                "    }," +
                "    __has__ : function(name) {" +
                "      for (var i=0;i<things.size();i++) { if (name == things.get(i).field.name) return true; }" +
                "      return name == 'clazz' || name == 'toString' || name == 'id' || name == 'wrapped-object' || name == 'statics';" +
                "    }," +
                "    __get__ : function(name) {" +
                "      if (name == 'clazz') {" +
                "        if (fldValueCache[name] == undefined) { fldValueCache[name] = wrapJavaObject(instance.javaClass); }" +
                "        return fldValueCache[name];" +
                "      } else if (name == 'statics') {" +
                "        if (fldValueCache[name] == undefined) { var clz = wrapJavaObject(instance.javaClass); fldValueCache[name] = clz != undefined ? clz.statics : null; }" +
                "        return fldValueCache[name];" +
                "      } else if (name == 'id') {" +
                "        if (fldValueCache[name] == undefined) { fldValueCache[name] = instance.instanceId; }" +
                "        return fldValueCache[name];" +
                "      } else if (name == 'wrapped-object') { return instance; }" +
                "      else { if (fldValueCache['_$'+name] == undefined) { fldValueCache['_$'+name] = wrapJavaObject(instance.getValueOfField(name)); } return fldValueCache['_$'+name]; }" +
                "    }," +
                "    __call__: function(name) {" +
                "      if (name == 'toString') {" +
                "        if (instance.javaClass.name == 'java.lang.String') { return FastStr.fastExtractStringValue(instance); }" +
                "        return instance.toString();" +
                "      } else { return undefined; }" +
                "    }" +
                "  };" +
                "};" +
                "JavaValueArrayWrapper = function(array) {" +
                "  var elements = array.values;" +
                "  var fldValueCache = new Array();" +
                "  return new JSAdapter() {" +
                "    __getIds__ : function() { var r = new Array(elements.size()); for (var i = 0; i < elements.size(); i++) { r[i] = String(i); } return r; }," +
                "    __has__: function(name) { return (name >= 0 && name < elements.size()) || name == 'length' || name == 'clazz' || name == 'toString' || name == 'wrapped-object'; }," +
                "    __get__: function(name) {" +
                "      if (name >= 0 && name < elements.size()) { return elements.get(name); }" +
                "      if (name == 'length') { if (fldValueCache['len'] == undefined) { fldValueCache['len'] = elements.size(); } return fldValueCache['len']; }" +
                "      else if (name == 'wrapped-object') { return array; }" +
                "      else if (name == 'clazz') { if (fldValueCache[name] == undefined) { fldValueCache[name] = wrapJavaObject(array.javaClass); } return fldValueCache[name]; }" +
                "      else { return undefined; }" +
                "    }," +
                "    __call__: function(name) {" +
                "      if (name == 'toString') {" +
                "        if (array.javaClass.name == 'char[]') { return FastStr.fastExtractStringValue(array); }" +
                "        return array.toString();" +
                "      } else { return undefined; }" +
                "    }" +
                "  };" +
                "};";

            scriptEngine.eval(jsPatch);
        } catch (Exception e) {
            // If patching fails, fall back to original slow path
        }
    }
}
