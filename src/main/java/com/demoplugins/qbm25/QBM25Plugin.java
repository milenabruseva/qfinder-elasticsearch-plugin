package com.demoplugins.qbm25;

import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.fielddata.ScriptDocValues;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.plugins.ScriptPlugin;
import org.elasticsearch.script.DocReader;
import org.elasticsearch.script.ScoreScript;
import org.elasticsearch.script.ScoreScript.LeafFactory;
import org.elasticsearch.script.ScriptContext;
import org.elasticsearch.script.ScriptEngine;
import org.elasticsearch.script.ScriptFactory;
import org.elasticsearch.search.lookup.SearchLookup;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import java.lang.Math;

public class QBM25Plugin extends Plugin implements ScriptPlugin {

    @Override
    public ScriptEngine getScriptEngine(Settings settings, Collection<ScriptContext<?>> contexts) {
        return new QBM25ScriptEngine();
    }

    // tag::expert_engine
    private static class QBM25ScriptEngine implements ScriptEngine {

        @Override
        public String getType() {
            return "expert_scripts";
        }

        @Override
        public <T> T compile(String scriptName, String scriptSource, ScriptContext<T> context, Map<String, String> params) {

            if (!context.equals(ScoreScript.CONTEXT)) {
                throw new IllegalArgumentException(getType() + " scripts cannot be used for context [" + context.name + "]");
            }

            // we use the script "source" as the script identifier
            if ("qbm25".equals(scriptSource)) {
                ScoreScript.Factory factory = new QBM25Factory();
                return context.factoryClazz.cast(factory);
            }

            throw new IllegalArgumentException("Unknown script name " + scriptSource);
        }

        @Override
        public void close() {
            // optionally close resources
        }

        @Override
        public Set<ScriptContext<?>> getSupportedContexts() {
            return Set.of(ScoreScript.CONTEXT);
        }

        private static class QBM25Factory implements ScoreScript.Factory, ScriptFactory {

            @Override
            public boolean isResultDeterministic() {
                // this implies the results are cacheable
                return true;
            }

            @Override
            public LeafFactory newFactory(Map<String, Object> params, SearchLookup lookup) {
                return new QBM25LeafFactory(params, lookup);
            }
        }

        private static class QBM25LeafFactory implements LeafFactory {

            private final Map<String, Object> params;
            private final SearchLookup lookup;
            private final String handler;
            private final String units;
            private final double amount;
            private final double amount2;
            private final double weight;
            private final double max_score;


            private QBM25LeafFactory(Map<String, Object> params, SearchLookup lookup) {

                if (!params.containsKey("handler")) {
                    throw new IllegalArgumentException("Missing parameter [handler]");
                }
                if (!params.containsKey("unit")) {
                    throw new IllegalArgumentException("Missing parameter [unit]");
                }
                if (!params.containsKey("amount")) {
                    throw new IllegalArgumentException("Missing parameter [amount]");
                }
                if (!params.containsKey("amount2")) {
                    throw new IllegalArgumentException("Missing parameter [amount2]");
                }
                if (!params.containsKey("weight")) {
                    throw new IllegalArgumentException("Missing parameter [weight]");
                }
                if (!params.containsKey("max_score")) {
                    throw new IllegalArgumentException("Missing parameter [max_score]");
                }

                this.params = params;
                this.lookup = lookup;
                handler = params.get("handler").toString();
                units = params.get("unit").toString();
                amount = Double.parseDouble(params.get("amount").toString());
                amount2 = Double.parseDouble(params.get("amount2").toString());
                weight = Double.parseDouble(params.get("weight").toString());
                if (params.get("max_score") == null){
                    max_score = 1.0;
                }
                else {
                    max_score = Double.parseDouble(params.get("max_score").toString());
                }
            }

            @Override
            public boolean needs_score() {
                return true;  // Return true if the script needs the score
            }

            @Override
            public ScoreScript newInstance(DocReader docReader) throws IOException {
                return new ScoreScript(params, lookup, docReader) {
                    @Override
                    public double execute(ExplanationHolder explanation) {
                        Map<String, ScriptDocValues<?>> doc = getDoc();
                        String unitsInDoc = ((ScriptDocValues.Strings) doc.get("units")).get(0);
                        String valuesInDoc = ((ScriptDocValues.Strings) doc.get("values")).get(0);

                        double normalized_score = get_score() / max_score;

                        unitsInDoc = unitsInDoc.replaceAll("^\\[|]$", "");
                        unitsInDoc = unitsInDoc.replaceAll("'", "");
                        unitsInDoc = unitsInDoc.replaceAll(", ", ",");

                        valuesInDoc = valuesInDoc.replaceAll("^\\[|]$", "");
                        valuesInDoc = valuesInDoc.replaceAll(", ", ",");

                        String unitsToSearchFor = units.replaceAll("^\\[|]$", "");
                        unitsToSearchFor = unitsToSearchFor.replaceAll("'", "");
                        unitsToSearchFor = unitsToSearchFor.replaceAll(", ", ",");

                        List<String> unitsList = new ArrayList<String>(Arrays.asList(unitsInDoc.split(",")));
                        List<String> valuesList = new ArrayList<String>(Arrays.asList(valuesInDoc.split(",")));
                        List<String> unitsToSearchForList = new ArrayList<String>(Arrays.asList(unitsToSearchFor.split(",")));

                        if(unitsToSearchForList.stream().noneMatch(unitsList::contains)) {
                            return normalized_score;
                        }

                        List<Double> valuesIntersection = new ArrayList<>();
                        for (int i = 0; i < unitsList.size(); i++) {
                            if(unitsToSearchForList.contains(unitsList.get(i))) {
                                valuesIntersection.add(Double.valueOf(valuesList.get(i)));
                            }
                        }

                        int numberOfUnits = valuesIntersection.size();
                        double dist = 0.0;
                        double result = 0.0;
                        switch (handler) {
                            case "=" -> {
                                for (Double number : valuesIntersection) {
                                    if (number != 0 && amount != 0) {
                                        dist += Math.exp(-Math.abs(amount - number));
                                    }
                                }
                                result = normalized_score + weight * (dist / numberOfUnits);
                            }
                            case ">" -> {
                                for (double number : valuesIntersection) {
                                    if (number != 0) {
                                        double difference = number - amount;
                                        dist += ((difference > 0) ? 1 : 0) * (amount / number);
                                    }
                                }
                                result = normalized_score + weight * (dist / numberOfUnits);
                            }
                            case "<" -> {
                                for (double number : valuesIntersection) {
                                    if (amount != 0) {
                                        double difference = amount - number;
                                        dist += ((difference > 0) ? 1 : 0) * (number / amount);
                                    }
                                }
                                result = normalized_score + weight * (dist / numberOfUnits);
                            }
                            case "<<" -> {
                                for (Double number : valuesIntersection) {
                                    double amount_avg = (amount + amount2) / 2.0;
                                    dist += Math.exp(-Math.abs(number - amount_avg));
                                }
                                result = normalized_score + weight * (dist / numberOfUnits);
                            }
                            default -> result = normalized_score;
                        }
                        if (result < 0){
                            result = 0.0;
                        }
                        return result;
                    }
                };
            }
        }
    }
    // end::expert_engine
}