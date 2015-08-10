package de.charite.compbio.exomiser.core;

import de.charite.compbio.exomiser.core.factories.*;
import de.charite.compbio.exomiser.core.filters.*;
import de.charite.compbio.exomiser.core.model.Gene;
import de.charite.compbio.exomiser.core.model.GeneticInterval;
import de.charite.compbio.exomiser.core.model.SampleData;
import de.charite.compbio.exomiser.core.model.VariantEvaluation;
import de.charite.compbio.exomiser.core.prioritisers.MockPrioritiser;
import de.charite.compbio.exomiser.core.prioritisers.Prioritiser;
import de.charite.compbio.exomiser.core.prioritisers.PriorityType;
import de.charite.compbio.jannovar.data.JannovarData;
import de.charite.compbio.jannovar.htsjdk.VariantContextAnnotator;
import org.junit.Ignore;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * @author Jules Jacobsen <jules.jacobsen@sanger.ac.uk>
 */
public class PassOnlyAnalysisRunnerTest {

    private static final Logger logger = LoggerFactory.getLogger(PassOnlyAnalysisRunnerTest.class);

    private PassOnlyAnalysisRunner instance;

    private final Path vcfPath = Paths.get("src/test/resources/smallTest.vcf");
    private final JannovarData testJannovarData = new TestJannovarDataFactory().getJannovarData();
    private final VariantContextAnnotator variantContextAnnotator = new VariantContextAnnotator(testJannovarData.getRefDict(), testJannovarData.getChromosomes());
    private final VariantFactory variantFactory = new VariantFactory(new VariantAnnotator(variantContextAnnotator));

    private final SampleDataFactory sampleDataFactory = new SampleDataFactory(variantFactory, testJannovarData);
    private final VariantDataService stubDataService = new VariantDataServiceStub();

    @Before
    public void setUp() {
        instance = new PassOnlyAnalysisRunner(sampleDataFactory, stubDataService);
    }

    private Analysis makeAnalysis(Path vcfPath, AnalysisStep... analysisSteps) {
        Analysis analysis = new Analysis();
        analysis.setVcfPath(vcfPath);
        if (analysisSteps.length != 0) {
            analysis.addAllSteps(Arrays.asList(analysisSteps));
        }
        return analysis;
    }

    private void printResults(SampleData sampleData) {
        for (Gene gene : sampleData.getGenes()) {
            logger.info("{}", gene);
            for (VariantEvaluation variantEvaluation : gene.getVariantEvaluations()) {
                logger.info("{}", variantEvaluation);
            }
        }
    }

    @Test
    public void testRunAnalysis_NoFiltersNoPrioritisers() {
        Analysis analysis = makeAnalysis(vcfPath, new AnalysisStep[]{});
        instance.runAnalysis(analysis);

        SampleData sampleData = analysis.getSampleData();
        printResults(sampleData);
        assertThat(sampleData.getGenes().size(), equalTo(2));
    }

    @Test
    public void testRunAnalysis_VariantFilterOnly() {
        VariantFilter intervalFilter = new IntervalFilter(new GeneticInterval(1, 145508800, 145508800));

        Analysis analysis = makeAnalysis(vcfPath, intervalFilter);
        instance.runAnalysis(analysis);

        SampleData sampleData = analysis.getSampleData();
        printResults(sampleData);
        assertThat(sampleData.getGenes().size(), equalTo(1));
        Gene passedGene = sampleData.getGenes().get(0);
        assertThat(passedGene.getGeneSymbol(), equalTo("RBM8A"));
        assertThat(passedGene.getVariantEvaluations().size(), equalTo(1));
    }

    @Test
    public void testRunAnalysis_PrioritiserAndPriorityScoreFilterOnly() {
        Integer expectedGeneId = 9939;
        Float desiredPrioritiserScore = 0.9f;
        Map<Integer, Float> geneIdPrioritiserScores = new HashMap<>();
        geneIdPrioritiserScores.put(expectedGeneId, desiredPrioritiserScore);

        PriorityType prioritiserTypeToMock = PriorityType.HIPHIVE_PRIORITY;
        Prioritiser prioritiser = new MockPrioritiser(prioritiserTypeToMock, geneIdPrioritiserScores);
        GeneFilter priorityScoreFilter = new PriorityScoreFilter(prioritiserTypeToMock, desiredPrioritiserScore - 0.1f);

        Analysis analysis = makeAnalysis(vcfPath, prioritiser, priorityScoreFilter);
        instance.runAnalysis(analysis);

        SampleData sampleData = analysis.getSampleData();
        printResults(sampleData);
        assertThat(sampleData.getGenes().size(), equalTo(1));
        Gene passed = sampleData.getGenes().get(0);
        assertThat(passed.passedFilters(), is(true));
        assertThat(passed.getEntrezGeneID(), equalTo(expectedGeneId));
        assertThat(passed.getPriorityScore(), equalTo(desiredPrioritiserScore));
    }

    @Test
    public void testRunAnalysis_PrioritiserPriorityScoreFilterVariantFilter() {
        Integer expectedGeneId = 9939;
        Float desiredPrioritiserScore = 0.9f;
        Map<Integer, Float> geneIdPrioritiserScores = new HashMap<>();
        geneIdPrioritiserScores.put(expectedGeneId, desiredPrioritiserScore);

        PriorityType prioritiserTypeToMock = PriorityType.HIPHIVE_PRIORITY;
        Prioritiser prioritiser = new MockPrioritiser(prioritiserTypeToMock, geneIdPrioritiserScores);
        GeneFilter priorityScoreFilter = new PriorityScoreFilter(prioritiserTypeToMock, desiredPrioritiserScore - 0.1f);
        VariantFilter intervalFilter = new IntervalFilter(new GeneticInterval(1, 145508800, 145508800));

        Analysis analysis = makeAnalysis(vcfPath, prioritiser, priorityScoreFilter, intervalFilter);
        instance.runAnalysis(analysis);

        SampleData sampleData = analysis.getSampleData();
        printResults(sampleData);
        assertThat(sampleData.getGenes().size(), equalTo(1));
        Gene passedGene = sampleData.getGenes().get(0);
        assertThat(passedGene.passedFilters(), is(true));
        assertThat(passedGene.getEntrezGeneID(), equalTo(expectedGeneId));
        assertThat(passedGene.getGeneSymbol(), equalTo("RBM8A"));
        assertThat(passedGene.getPriorityScore(), equalTo(desiredPrioritiserScore));
        assertThat(passedGene.getNumberOfVariants(), equalTo(1));
        VariantEvaluation passedVariantEvaluation =  passedGene.getVariantEvaluations().get(0);
        assertThat(passedVariantEvaluation.getChromosome(), equalTo(1));
        assertThat(passedVariantEvaluation.getPosition(), equalTo(145508800));
        assertThat(passedVariantEvaluation.getGeneSymbol(), equalTo(passedGene.getGeneSymbol()));
    }

}
