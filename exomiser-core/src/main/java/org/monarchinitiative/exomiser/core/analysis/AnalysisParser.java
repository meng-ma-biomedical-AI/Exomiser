/*
 * The Exomiser - A tool to annotate and prioritize genomic variants
 *
 * Copyright (c) 2016-2018 Queen Mary University of London.
 * Copyright (c) 2012-2016 Charité Universitätsmedizin Berlin and Genome Research Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.monarchinitiative.exomiser.core.analysis;

import de.charite.compbio.jannovar.annotation.VariantEffect;
import de.charite.compbio.jannovar.mendel.ModeOfInheritance;
import de.charite.compbio.jannovar.reference.HG19RefDictBuilder;
import org.monarchinitiative.exomiser.core.filters.*;
import org.monarchinitiative.exomiser.core.genome.GenomeAnalysisService;
import org.monarchinitiative.exomiser.core.genome.GenomeAnalysisServiceProvider;
import org.monarchinitiative.exomiser.core.genome.GenomeAssembly;
import org.monarchinitiative.exomiser.core.model.GeneticInterval;
import org.monarchinitiative.exomiser.core.model.frequency.FrequencySource;
import org.monarchinitiative.exomiser.core.model.pathogenicity.PathogenicitySource;
import org.monarchinitiative.exomiser.core.prioritisers.*;
import org.monarchinitiative.exomiser.core.writers.OutputFormat;
import org.monarchinitiative.exomiser.core.writers.OutputSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import static java.nio.file.Files.newInputStream;

/**
 * @since 7.0.0
 * @author Jules Jacobsen <jules.jacobsen@sanger.ac.uk>
 */
@Component
public class AnalysisParser {

    private static final Logger logger = LoggerFactory.getLogger(AnalysisParser.class);

    private final PriorityFactory prioritiserFactory;
    private final GenomeAnalysisServiceProvider genomeAnalysisServiceProvider;

    @Autowired
    public AnalysisParser(PriorityFactory prioritiserFactory, GenomeAnalysisServiceProvider genomeAnalysisServiceProvider) {
        this.prioritiserFactory = prioritiserFactory;
        this.genomeAnalysisServiceProvider = genomeAnalysisServiceProvider;
    }

    public Analysis parseAnalysis(Path analysisScript) {
        Map settingsMap = loadMap(analysisScript);
        return constructAnalysisFromMap(settingsMap);
    }

    public Analysis parseAnalysis(String analysisDoc) {
        Map settingsMap = loadMap(analysisDoc);
        return constructAnalysisFromMap(settingsMap);
    }

    public OutputSettings parseOutputSettings(Path analysisScript) {
        Map settingsMap = loadMap(analysisScript);
        return constructOutputSettingsFromMap(settingsMap);
    }

    public OutputSettings parseOutputSettings(String analysisDoc) {
        Map settingsMap = loadMap(analysisDoc);
        return constructOutputSettingsFromMap(settingsMap);
    }

    private Map loadMap(String analysisDoc) {
        Yaml yaml = new Yaml();
        return (Map) yaml.load(analysisDoc);
    }

    private Map loadMap(Path analysisScript) {
        Yaml yaml = new Yaml();
        try (InputStream inputStream = newInputStream(analysisScript)) {
            CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder();
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream, decoder));
            return (Map) yaml.load(bufferedReader);
        } catch (IOException ex) {
            throw new AnalysisFileNotFoundException("Unable to find analysis file: " + ex.getMessage(), ex);
        }
    }

    private Analysis constructAnalysisFromMap(Map settingsMap) {
        AnalysisConstructor analysisConstructor = new AnalysisConstructor();
        return analysisConstructor.construct((Map) settingsMap.get("analysis"));
    }

    private OutputSettings constructOutputSettingsFromMap(Map settingsMap) {
        OutputSettingsConstructor outputSettingsConstructor = new OutputSettingsConstructor();
        return outputSettingsConstructor.construct((Map) settingsMap.get("outputOptions"));
    }

    protected static class AnalysisFileNotFoundException extends RuntimeException {
        AnalysisFileNotFoundException(String message, Exception e) { super(message, e);}
    }

    private class OutputSettingsConstructor {

        public OutputSettings construct(Map analysisMap) {
            return OutputSettings.builder()
                    .outputPassVariantsOnly(parseOutputPassVariantsOnly(analysisMap))
                    .numberOfGenesToShow(parseNumberOfGenesToShow(analysisMap))
                    .outputPrefix(parseOutputPrefix(analysisMap))
                    .outputFormats(parseOutputFormats(analysisMap))
                    .build();
        }

        private Boolean parseOutputPassVariantsOnly(Map<String, Boolean> analysisMap) {
            Boolean outputPassOnly = analysisMap.get("outputPassVariantsOnly");
            if (outputPassOnly == null) {
                throw new AnalysisParserException("outputPassVariantsOnly cannot be null.", analysisMap);
            }
            return outputPassOnly;
        }

        private int parseNumberOfGenesToShow(Map<String, Integer> analysisMap) {
            Integer genesToShow = analysisMap.get("numGenes");
            if (genesToShow == null) {
                genesToShow = 0;
            }
            return genesToShow;
        }

        private String parseOutputPrefix(Map<String, String> analysisMap) {
            String outputPrefix = analysisMap.get("outputPrefix");
            if (outputPrefix == null) {
                outputPrefix = "exomiser-results";
            }
            return outputPrefix;
        }

        private Set<OutputFormat> parseOutputFormats(Map<String, List<String>> analysisMap) {
            List<String> givenOutputFormats = analysisMap.getOrDefault("outputFormats", Collections.emptyList());
            if (givenOutputFormats == null || givenOutputFormats.isEmpty()) {
                logger.info("No output format options supplied.");
                return EnumSet.noneOf(OutputFormat.class);
            }
            Set<OutputFormat> parsedOutputFormats = new LinkedHashSet<>();
            for (String name : givenOutputFormats) {
                switch (name.trim().toUpperCase()) {
                    case "HTML":
                        parsedOutputFormats.add(OutputFormat.HTML);
                        break;
                    case "TSV_GENE":
                    case "TAB-GENE":
                    case "TSV-GENE":
                        parsedOutputFormats.add(OutputFormat.TSV_GENE);
                        break;
                    case "TSV_VARIANT":
                    case "TAB-VARIANT":
                    case "TSV-VARIANT":
                        parsedOutputFormats.add(OutputFormat.TSV_VARIANT);
                        break;
                    case "VCF":
                        parsedOutputFormats.add(OutputFormat.VCF);
                        break;
                    case "PHENOGRID":
                        parsedOutputFormats.add(OutputFormat.PHENOGRID);
                        break;
                    default:
                        logger.info("{} is not a recognised output format. Please choose one or more of HTML, TAB-GENE, TAB-VARIANT, VCF - defaulting to HTML", name);
                        parsedOutputFormats.add(OutputFormat.HTML);
                        break;
                }
            }
            return EnumSet.copyOf(parsedOutputFormats);
        }
    }

    private class AnalysisConstructor {

        private GenomeAssembly defaultAssembly = GenomeAssembly.HG19;
        private GenomeAnalysisService genomeAnalysisService;

        public Analysis construct(Map analysisMap) {

            GenomeAssembly requestedAssembly = parseGenomeAssembly(analysisMap);
            genomeAnalysisService = genomeAnalysisServiceProvider.get(requestedAssembly);

            Analysis analysis = Analysis.builder()
                    .vcfPath(parseVcf(analysisMap))
                    .genomeAssembly(requestedAssembly)
                    .pedPath(parsePed(analysisMap))
                    .probandSampleName(parseProbandSampleName(analysisMap))
                    .hpoIds(parseHpoIds(analysisMap))
                    .modeOfInheritance(parseModeOfInheritance(analysisMap))
                    .analysisMode(parseAnalysisMode(analysisMap))
                    .frequencySources(parseFrequencySources(analysisMap))
                    .pathogenicitySources(parsePathogenicitySources(analysisMap))
                    .steps(makeAnalysisSteps(analysisMap))
                    .build();
            //this method is only here to provide a warning to users that their script is out of date.
            parseScoringMode(analysisMap);

            logger.debug("Made analysis: {}", analysis);
            return analysis;
        }

        private List<AnalysisStep> makeAnalysisSteps(Map analysisMap) {
            List<AnalysisStep> analysisSteps = new ArrayList<>();
            for (Map<String, Map> analysisStepMap : parseAnalysisSteps(analysisMap)) {
                logger.debug("Analysis step: {}", analysisStepMap);
                for (Entry<String, Map> entry : analysisStepMap.entrySet()) {
                    AnalysisStep analysisStep = makeAnalysisStep(entry, analysisMap);
                    if (analysisStep != null) {
                        analysisSteps.add(analysisStep);
                        logger.debug("Added {}", entry.getKey());
                    }
                }
            }
            //should this be optional for people really wanting to screw about with the steps at the risk of catastrophic failure?
            //it's really an optimiser step of a compiler.
            new AnalysisStepChecker().check(analysisSteps);
            return analysisSteps;
        }

        private Path parseVcf(Map<String, String> analysisMap) {
            String vcfValue = analysisMap.get("vcf");
            //VCF file paths are not allowed to be null
            if (vcfValue == null) {
                throw new AnalysisParserException("VCF path cannot be null.", analysisMap);
            }
            return Paths.get(vcfValue);
        }

        private GenomeAssembly parseGenomeAssembly(Map<String, String> analysisMap) {
            String genomeAssemblyValue = analysisMap.get("genomeAssembly");
            //VCF file paths are not allowed to be null
            if (genomeAssemblyValue == null || genomeAssemblyValue.isEmpty()) {
                logger.info("genomeAssembly not specified - will use default: {}", defaultAssembly);
                return defaultAssembly;
            }
            return GenomeAssembly.fromValue(genomeAssemblyValue);
        }

        private Path parsePed(Map<String, String> analysisMap) {
            String pedValue = analysisMap.get("ped");
            //PED file paths are allowed to be null
            if (pedValue == null) {
                return null;
            }
            return Paths.get(pedValue);
        }

        private String parseProbandSampleName(Map<String, String> analysisMap) {
            String probandSampleName = analysisMap.get("proband");
            //probandSampleName is allowed to be null, but may throw exceptions when the VCF/PED file is checked
            if (probandSampleName == null) {
                return "";
            }
            return probandSampleName;
        }

        private List<String> parseHpoIds(Map<String, List<String>> analysisMap) {
            List<String> hpoIds = analysisMap.get("hpoIds");
            if (hpoIds == null) {
                return new ArrayList<>();
            }
            return hpoIds;
        }

        private Set<ModeOfInheritance> parseModeOfInheritance(Map<String, Object> analysisMap) {
            Object modeOfInheritanceInput = analysisMap.get("modeOfInheritance");

            if (String.class.isInstance(modeOfInheritanceInput)) {
                //Pre 10.0.0 version - only expected a single string value
                String value = (String) modeOfInheritanceInput;
                return parseModesOfInheritanceFromList(Collections.singletonList(value));
            }
            if (List.class.isInstance(modeOfInheritanceInput)) {
                return parseModesOfInheritanceFromList((List<String>) modeOfInheritanceInput);
            }
            return Analysis.DEFAULT_INHERITANCE_MODES;
        }


        private Set<ModeOfInheritance> parseModesOfInheritanceFromList(List<String> inheritanceModes) {
            if (inheritanceModes == null || inheritanceModes.isEmpty()) {
                return Collections.emptySet();
            }
            if (inheritanceModes.equals(Collections.singletonList("UNDEFINED")) || inheritanceModes.equals(Collections.singletonList("UNINITIALIZED")) || inheritanceModes.equals(Collections.singletonList("ANY"))) {
                return Collections.emptySet();
            }
            return inheritanceModes.stream()
                    .map(this::parseValueOfInheritanceMode)
                    .filter(mode -> mode != ModeOfInheritance.ANY)
                    .collect(Collectors.toSet());
        }

        private ModeOfInheritance parseValueOfInheritanceMode(String value) {
            try {
                return ModeOfInheritance.valueOf(value);
            } catch (IllegalArgumentException e) {
                throw new AnalysisParserException(String.format("'%s' is not a valid mode of inheritance. Use one of: %s", value, Arrays.toString(ModeOfInheritance.values())));
            }
        }

        private AnalysisMode parseAnalysisMode(Map<String, String> analysisMap) {
            String value = analysisMap.get("analysisMode");
            if (value == null) {
                return AnalysisMode.PASS_ONLY;
            }
            return AnalysisMode.valueOf(value);
        }

        @Deprecated
        private void parseScoringMode(Map<String, String> analysisMap) {
            String value = analysisMap.get("geneScoreMode");
            if (value != null) {
                logger.info("geneScoreMode is deprecated and {} will have no effect. " +
                        "Please consider removing this from your analysis script to prevent this message from showing again.", value);
            }
        }

        private List<Map<String, Map>> parseAnalysisSteps(Map analysisMap) {
            List steps = (List) analysisMap.get("steps");
            if (steps == null) {
                steps = new ArrayList();
            }
            return steps;
        }

        /**
         * Returns an AnalysisStep or null if the step is unrecognised.
         *
         * @return
         */
        private AnalysisStep makeAnalysisStep(Entry<String, Map> entry, Map analysisMap) {
            String key = entry.getKey();
            Map analysisStepMap = entry.getValue();
            switch (key) {
                case "failedVariantFilter":
                    return makeFailedVariantFilter();
                case "intervalFilter":
                    return makeIntervalFilter(analysisStepMap);
                case "genePanelFilter":
                    return makeGeneSymbolFilter(analysisStepMap);
                case "variantEffectFilter":
                    return makeVariantEffectFilter(analysisStepMap);
                case "qualityFilter":
                    return makeQualityFilter(analysisStepMap);
                case "knownVariantFilter":
                    return makeKnownVariantFilter(analysisStepMap, parseFrequencySources(analysisMap));
                case "frequencyFilter":
                    return makeFrequencyFilter(analysisStepMap, parseFrequencySources(analysisMap));
                case "pathogenicityFilter":
                    return makePathogenicityFilter(analysisStepMap, parsePathogenicitySources(analysisMap));
                case "inheritanceFilter":
                    return makeInheritanceFilter(parseModeOfInheritance(analysisMap));
                case "priorityScoreFilter":
                    return makePriorityScoreFilter(analysisStepMap);
                case "regulatoryFeatureFilter":
                    return makeRegulatoryFeatureFilter();
                case "omimPrioritiser":
                    return prioritiserFactory.makeOmimPrioritiser();
                case "hiPhivePrioritiser":
                    return makeHiPhivePrioritiser(analysisStepMap);
                case "phivePrioritiser":
                    return prioritiserFactory.makePhivePrioritiser();
                case "phenixPrioritiser":
                    return prioritiserFactory.makePhenixPrioritiser();
                case "exomeWalkerPrioritiser":
                    return makeWalkerPrioritiser(analysisStepMap);
                default:
                    //throw exception here?
                    logger.error("Unsupported exomiser step: {}", key);
                    return null;
            }
        }

        private FailedVariantFilter makeFailedVariantFilter() {
            return new FailedVariantFilter();
        }

        private IntervalFilter makeIntervalFilter(Map<String, String> options) {
            String interval = options.get("interval");
            if (interval == null) {
                throw new AnalysisParserException("Interval filter requires a valid genetic interval e.g. {interval: 'chr10:122892600-122892700'}", options);
            }
            return new IntervalFilter(GeneticInterval.parseString(HG19RefDictBuilder.build(), interval));
        }

        private GeneSymbolFilter makeGeneSymbolFilter(Map<String, List<String>> options) {
            List<String> geneSymbols = options.get("geneSymbols");
            if (geneSymbols == null || geneSymbols.isEmpty()) {
                throw new AnalysisParserException("Gene panel filter requires a list of HGNC gene symbols e.g. {geneSymbols: [FGFR1, FGFR2]}", options);
            }
            return new GeneSymbolFilter(new LinkedHashSet<>(geneSymbols));
        }

        private VariantEffectFilter makeVariantEffectFilter(Map<String, List<String>> options) {
            List<String> effectsToRemove = options.get("remove");
            if (effectsToRemove == null) {
                throw new AnalysisParserException("VariantEffect filter requires a list of VariantEffects to be removed e.g. {remove: [UPSTREAM_GENE_VARIANT, INTERGENIC_VARIANT, SYNONYMOUS_VARIANT]}", options);
            }
            List<VariantEffect> variantEffects = new ArrayList<>();
            for (String effect : effectsToRemove) {
                try {
                    VariantEffect variantEffect = VariantEffect.valueOf(effect);
                    variantEffects.add(variantEffect);
                } catch (IllegalArgumentException ex) {
                    throw new AnalysisParserException(String.format("Illegal VariantEffect: '%s'.%nPermitted effects are any of: %s.", effect, EnumSet.allOf(VariantEffect.class)), options, ex);
                }
            }
            return new VariantEffectFilter(EnumSet.copyOf(variantEffects));
        }

        private QualityFilter makeQualityFilter(Map<String, Double> options) {
            Double quality = options.get("minQuality");
            if (quality == null) {
                throw new AnalysisParserException("Quality filter requires a floating point value for the minimum PHRED score e.g. {minQuality: 50.0}", options);
            }
            return new QualityFilter(quality);
        }

        private VariantFilter makeKnownVariantFilter(Map<String, Object> options, Set<FrequencySource> sources) {
            //nothing special to do here, apart from wrap the filter with a DataProvider, this is a boolean filter.
            if (sources.isEmpty()) {
                throw new AnalysisParserException("Known variant filter requires a list of frequency sources for the analysis e.g. frequencySources: [THOUSAND_GENOMES, ESP_ALL]", options);
            }
            return new FrequencyDataProvider(genomeAnalysisService, EnumSet.copyOf(sources), new KnownVariantFilter());
        }

        private VariantFilter makeFrequencyFilter(Map<String, Object> options, Set<FrequencySource> sources) {
            Double maxFreq = getMaxFrequency(options);
            if (sources.isEmpty()) {
                throw new AnalysisParserException("Frequency filter requires a list of frequency sources for the analysis e.g. frequencySources: [THOUSAND_GENOMES, ESP_ALL]", options);
            }
            return new FrequencyDataProvider(genomeAnalysisService, EnumSet.copyOf(sources), new FrequencyFilter(maxFreq
                    .floatValue()));
        }

        private Double getMaxFrequency(Map<String, Object> options) {
            Double maxFreq = (Double) options.get("maxFrequency");
            if (maxFreq == null) {
                throw new AnalysisParserException("Frequency filter requires a floating point value for the maximum frequency e.g. {maxFrequency: 1.0}", options);
            }
            return maxFreq;
        }

        private Set<FrequencySource> parseFrequencySources(Map<String, List<String>> options) {
            List<String> frequencySources = options.get("frequencySources");
            if (frequencySources == null) {
                return EnumSet.noneOf(FrequencySource.class);
            }
            List<FrequencySource> sources = new ArrayList<>();
            for (String source : frequencySources) {
                try {
                    FrequencySource frequencySource = FrequencySource.valueOf(source);
                    sources.add(frequencySource);
                } catch (IllegalArgumentException ex) {
                    throw new AnalysisParserException(String.format("Illegal FrequencySource: '%s'.%nPermitted sources are any of: %s.", source, EnumSet.allOf(FrequencySource.class)), options, ex);
                }
            }
            if (sources.isEmpty()) {
                return EnumSet.noneOf(FrequencySource.class);
            }
            return EnumSet.copyOf(sources);
        }

        private VariantFilter makePathogenicityFilter(Map<String, Object> options, Set<PathogenicitySource> sources) {
            Boolean keepNonPathogenic = (Boolean) options.get("keepNonPathogenic");
            if (keepNonPathogenic == null) {
                throw new AnalysisParserException("Pathogenicity filter requires a boolean value for keepNonPathogenic e.g. {keepNonPathogenic: false}", options);
            }
            if (sources.isEmpty()) {
                throw new AnalysisParserException("Pathogenicity filter requires a list of pathogenicity sources for the analysis e.g. {pathogenicitySources: [SIFT, POLYPHEN, MUTATION_TASTER]}", options);
            }
            return new PathogenicityDataProvider(genomeAnalysisService, EnumSet.copyOf(sources), new PathogenicityFilter(keepNonPathogenic));
        }

        private Set<PathogenicitySource> parsePathogenicitySources(Map<String, List<String>> options) {
            List<String> pathogenicitySources = options.get("pathogenicitySources");
            if (pathogenicitySources == null) {
                return EnumSet.noneOf(PathogenicitySource.class);
            }
            List<PathogenicitySource> sources = new ArrayList<>();
            for (String source : pathogenicitySources) {
                try {
                    PathogenicitySource pathogenicitySource = PathogenicitySource.valueOf(source);
                    sources.add(pathogenicitySource);
                } catch (IllegalArgumentException ex) {
                    throw new AnalysisParserException(String.format("Illegal PathogenicitySource: '%s'.%nPermitted sources are any of: %s.", source, EnumSet.allOf(PathogenicitySource.class)), options, ex);
                }
            }
            if (sources.isEmpty()) {
                return EnumSet.noneOf(PathogenicitySource.class);
            }
            return EnumSet.copyOf(sources);
        }

        private PriorityScoreFilter makePriorityScoreFilter(Map<String, Object> options) {
            String priorityTypeString = (String) options.get("priorityType");
            if (priorityTypeString == null) {
                throw new AnalysisParserException("Priority score filter requires a string value for the prioritiser type e.g. {priorityType: HIPHIVE_PRIORITY}", options);
            }
            PriorityType priorityType = PriorityType.valueOf(priorityTypeString);

            Double minPriorityScore = (Double) options.get("minPriorityScore");
            if (minPriorityScore == null) {
                throw new AnalysisParserException("Priority score filter requires a floating point value for the minimum prioritiser score e.g. {minPriorityScore: 0.65}", options);
            }
            return new PriorityScoreFilter(priorityType, minPriorityScore.floatValue());
        }

        private VariantFilter makeRegulatoryFeatureFilter() {
            return new RegulatoryFeatureFilter();
        }

        private InheritanceFilter makeInheritanceFilter(Set<ModeOfInheritance> modeOfInheritance) {
            if (modeOfInheritance.isEmpty() || modeOfInheritance.equals(InheritanceFilter.JUST_ANY)) {
                logger.info("Not making an inheritance filter for {} mode of inheritance", modeOfInheritance);
                return null;
            }
            return new InheritanceFilter(modeOfInheritance);
        }

        private HiPhivePriority makeHiPhivePrioritiser(Map<String, String> options) {
            HiPhiveOptions hiPhiveOptions = makeHiPhiveOptions(options);
            logger.info("Made {}", hiPhiveOptions);
            return prioritiserFactory.makeHiPhivePrioritiser(hiPhiveOptions);
        }

        private HiPhiveOptions makeHiPhiveOptions(Map<String, String> options) {
            if (!options.isEmpty()) {
                String diseaseId = options.getOrDefault("diseaseId", "");
                String candidateGeneSymbol = options.getOrDefault("candidateGeneSymbol", "");
                String runParams = options.getOrDefault("runParams", "");

                return HiPhiveOptions.builder()
                        .diseaseId(diseaseId)
                        .candidateGeneSymbol(candidateGeneSymbol)
                        .runParams(runParams)
                        .build();
            }
            return HiPhiveOptions.DEFAULT;
        }

        private ExomeWalkerPriority makeWalkerPrioritiser(Map<String, List<Integer>> options) {
            List<Integer> geneIds = options.get("seedGeneIds");
            if (geneIds == null || geneIds.isEmpty()) {
                throw new AnalysisParserException("ExomeWalker prioritiser requires a list of ENTREZ geneIds e.g. {seedGeneIds: [11111, 22222, 33333]}", options);
            }
            return prioritiserFactory.makeExomeWalkerPrioritiser(geneIds);
        }

    }

    protected static class AnalysisParserException extends RuntimeException {

        AnalysisParserException(String message) {
            super(message);
        }

        AnalysisParserException(String message, Map options) {
            super(message + " was " + new Yaml().dump(options));
        }

        AnalysisParserException(String message, Map options, Exception e) {
            super(message + " was " + new Yaml().dump(options), e);
        }
    }

}
