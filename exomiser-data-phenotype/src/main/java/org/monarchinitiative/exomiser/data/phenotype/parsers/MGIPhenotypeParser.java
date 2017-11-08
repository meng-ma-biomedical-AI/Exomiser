/*
 * The Exomiser - A tool to annotate and prioritize genomic variants
 *
 * Copyright (c) 2016-2017 Queen Mary University of London.
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

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.monarchinitiative.exomiser.data.phenotype.parsers;

import com.google.common.base.Joiner;
import org.monarchinitiative.exomiser.data.phenotype.resources.Resource;
import org.monarchinitiative.exomiser.data.phenotype.resources.ResourceOperationStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**

 *
 * @author Jules Jacobsen <jules.jacobsen@sanger.ac.uk>
 */
public class MGIPhenotypeParser implements ResourceParser {

    private static final Logger logger = LoggerFactory.getLogger(MGIPhenotypeParser.class);

    private final Map<String, String> mouse2PhenotypesMap;
    private final Map<String, String> mouse2geneMap;

    public MGIPhenotypeParser(Map<String, String> mouse2PhenotypesMap, Map<String, String> mouse2GeneMap) {
        this.mouse2geneMap = mouse2GeneMap;
        this.mouse2PhenotypesMap = mouse2PhenotypesMap;
    }

    @Override
    public void parseResource(Resource resource, Path inDir, Path outDir) {
        Path inFile = inDir.resolve(resource.getExtractedFileName());
        Path outFile = outDir.resolve(resource.getParsedFileName());
        logger.info("Parsing {} file: {}. Writing out to: {}", resource.getName(), inFile, outFile);
        ResourceOperationStatus status;
        Map<String, Set <String>> mouse2PhenotypeMap = new HashMap<>();
        //Map<String, String> mouse2geneMap = new HashMap<>();
        try (BufferedReader reader = Files.newBufferedReader(inFile, Charset.defaultCharset());
            BufferedWriter writer = Files.newBufferedWriter(outFile, Charset.defaultCharset())) {
            String line;
            while ((line = reader.readLine()) != null) {
                //logger.info("Line is " + line);
                String[] fields = line.split("\\t");
                String mpId = fields[4];
                String modelID = fields[0] + fields[3];
                mouse2geneMap.put(modelID,fields[6]);
                if (mouse2PhenotypeMap.containsKey(modelID)) {
                    mouse2PhenotypeMap.get(modelID).add(mpId);
                }
                else{
                    Set<String> mpIds = new HashSet<>();
                    mpIds.add(mpId);
                    mouse2PhenotypeMap.put(modelID,mpIds);
                }
            }
            int id = 1;
            for (String modelId: mouse2PhenotypeMap.keySet()) {
                Set <String>  mpIds = mouse2PhenotypeMap.get(modelId);
                mouse2PhenotypesMap.put(modelId,Joiner.on(",").join(mpIds));
                //String[] mgiIds = mouse2geneMap.get(modelId).split(",");
                //for (String mgiID : mgiIds) {
                    //writer.write(String.format("%s|%s|%s|%s%n", mgiID, "", id, Joiner.on(",").join(mpIds)));
                //}
                id++;
            }
            status = ResourceOperationStatus.SUCCESS;

        } catch (FileNotFoundException ex) {
            logger.error(null, ex);
            status = ResourceOperationStatus.FILE_NOT_FOUND;
        } catch (IOException ex) {
            logger.error(null, ex);
            status = ResourceOperationStatus.FAILURE;
        }
        logger.info("{}", status);
        resource.setParseStatus(status);
    }
}
