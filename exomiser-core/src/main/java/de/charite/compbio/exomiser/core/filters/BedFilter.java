package de.charite.compbio.exomiser.core.filters;

import de.charite.compbio.exomiser.core.model.VariantEvaluation;
import java.util.Objects;
import java.util.Set;

public class BedFilter implements VariantFilter {

    private static final FilterType filterType = FilterType.BED_FILTER;

    //add a token failed score - this is essentially a boolean pass/fail so we're using 0 here.
    FilterResult passesScore = new PassFilterResult(filterType, 1f);
    FilterResult failedScore = new FailFilterResult(filterType, 0f);

    /**
     * A set of off-target variant types such as Intergenic that we will
     * runFilter out from further consideration.
     */
    private final Set<String> targetGeneSymbols;

    /**
     * A set of human gene symbols which the sample will be filtered against. 
     * @param geneSymbols
     */
    public BedFilter(Set<String> geneSymbols) {
        this.targetGeneSymbols = geneSymbols;
    }

    public Set<String> getTargetGeneSymbols() {
        return targetGeneSymbols;
    }

    /**
     * @return an integer constant (as defined in exomizer.common.Constants)
     * that will act as a flag to generate the output HTML dynamically depending
     * on the filters that the user has chosen.
     */
    @Override
    public FilterType getFilterType() {
        return filterType;
    }

    @Override
    public FilterResult runFilter(VariantEvaluation variantEvaluation) {

        if (variantEvaluation.isOffExome()) {
            return failedScore;
        }
        String geneSymbol = variantEvaluation.getGeneSymbol();
        if (!targetGeneSymbols.contains(geneSymbol)) {
            return failedScore;
        }
        return passesScore;
    }

    protected FilterReport makeReport(Set<String> nontargetGenes, int passed, int failed) {

        FilterReport report = new FilterReport(filterType, passed, failed);

        report.addMessage(String.format("Removed a total of %d off-target variants from further consideration", failed));

        StringBuilder sb = new StringBuilder();

        if (!nontargetGenes.isEmpty()) {
            sb.append(String.format("Variants were found in %d off target genes: ", nontargetGenes.size()));
            boolean notfirst = false;
            for (String gene : nontargetGenes) {
                if (notfirst) {
                    sb.append(", ");
                }
                notfirst = true;
                sb.append(gene);
            }
            sb.append(". Variants in these off-target genes were not considered further in the analysis.");
            report.addMessage(sb.toString());
        }
        return report;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 73 * hash + Objects.hashCode(this.filterType);
        hash = 73 * hash + Objects.hashCode(this.targetGeneSymbols);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final BedFilter other = (BedFilter) obj;
        if (!Objects.equals(this.targetGeneSymbols, other.targetGeneSymbols)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "BedFilter{" + "targetGeneSymbols=" + targetGeneSymbols + '}';
    }
    
}
