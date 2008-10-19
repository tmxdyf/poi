/* ====================================================================
   Licensed to the Apache Software Foundation (ASF) under one or more
   contributor license agreements.  See the NOTICE file distributed with
   this work for additional information regarding copyright ownership.
   The ASF licenses this file to You under the Apache License, Version 2.0
   (the "License"); you may not use this file except in compliance with
   the License.  You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
==================================================================== */
package org.apache.poi.util;

import org.openxml4j.opc.*;
import org.openxml4j.opc.Package;
import org.openxml4j.opc.internal.PackagePropertiesPart;
import org.openxml4j.opc.internal.marshallers.PackagePropertiesMarshaller;
import org.openxml4j.exceptions.OpenXML4JException;
import org.apache.poi.util.IOUtils;

import java.io.*;
import java.util.ArrayList;
import java.lang.reflect.Method;

/**
 * Provides handy methods to work with OOXML packages
 *
 * @author Yegor Kozlov
 */
public class PackageHelper {

    /**
     * Clone the specified package.
     *
     * @param   pkg   the package to clone
     * @return  the cloned package
     */
    public static Package clone(Package pkg) throws OpenXML4JException, IOException {
        return clone(pkg, createTempFile());
    }

    /**
     * Clone the specified package.
     *
     * @param   pkg   the package to clone
     * @param   file  the destination file
     * @return  the cloned package
     */
    public static Package clone(Package pkg, File file) throws OpenXML4JException, IOException {

        String path = file.getAbsolutePath();

        Package dest = Package.create(path);
        PackageRelationshipCollection rels = pkg.getRelationships();
        for (PackageRelationship rel : rels) {
            PackagePart part = pkg.getPart(rel);
            PackagePart part_tgt;
            if (rel.getRelationshipType().equals(PackageRelationshipTypes.CORE_PROPERTIES)) {
                copyProperties(pkg.getPackageProperties(), dest.getPackageProperties());
                continue;
            } else {
                dest.addRelationship(part.getPartName(), rel.getTargetMode(), rel.getRelationshipType());
                part_tgt = dest.createPart(part.getPartName(), part.getContentType());
            }

            OutputStream out = part_tgt.getOutputStream();
            IOUtils.copy(part.getInputStream(), out);
            out.close();

            if(part.hasRelationships()) {
                copy(pkg, part, dest, part_tgt);
            }
        }
        dest.close();

        //the temp file will be deleted when JVM terminates
        new File(path).deleteOnExit();
        return Package.open(path);
    }

    /**
     *
     * @return
     * @throws IOException
     */
    public static File createTempFile() throws IOException {
        File file = File.createTempFile("poi-ooxml-", ".tmp");
        //there is no way to pass an existing file to Package.create(file),
        //delete first, the file will be re-created in Packe.create(file)
        file.delete();
        file.deleteOnExit();
        return file;

    }

    /**
     * Recursively copy package parts to the destination package
     */
    private static void copy(Package pkg, PackagePart part, Package tgt, PackagePart part_tgt) throws OpenXML4JException, IOException {
        PackageRelationshipCollection rels = part.getRelationships();
        if(rels != null) for (PackageRelationship rel : rels) {
            PackagePart p;
            if(rel.getTargetMode() == TargetMode.EXTERNAL){
                part_tgt.addExternalRelationship(rel.getTargetURI().toString(), rel.getRelationshipType(), rel.getId());
                //external relations don't have associated package parts
                continue;
            } else {
                PackagePartName relName = PackagingURIHelper.createPartName(rel.getTargetURI());
                p = pkg.getPart(relName);
            }

            part_tgt.addRelationship(p.getPartName(), rel.getTargetMode(), rel.getRelationshipType(), rel.getId());

            PackagePart dest;
            if(!tgt.containPart(p.getPartName())){
                dest = tgt.createPart(p.getPartName(), p.getContentType());
                OutputStream out = dest.getOutputStream();
                IOUtils.copy(p.getInputStream(), out);
                out.close();
                copy(pkg, p, tgt, dest);
            }
        }
    }

    /**
     * Copy core package properties
     *
     * @param src source properties
     * @param tgt target properties
     */
    private static void copyProperties(PackageProperties src, PackageProperties tgt){
        tgt.setCategoryProperty(src.getCategoryProperty().getValue());
        tgt.setContentStatusProperty(src.getContentStatusProperty().getValue());
        tgt.setContentTypeProperty(src.getContentTypeProperty().getValue());
        tgt.setCreatorProperty(src.getCreatorProperty().getValue());
        tgt.setDescriptionProperty(src.getDescriptionProperty().getValue());
        tgt.setIdentifierProperty(src.getIdentifierProperty().getValue());
        tgt.setKeywordsProperty(src.getKeywordsProperty().getValue());
        tgt.setLanguageProperty(src.getLanguageProperty().getValue());
        tgt.setRevisionProperty(src.getRevisionProperty().getValue());
        tgt.setSubjectProperty(src.getSubjectProperty().getValue());
        tgt.setTitleProperty(src.getTitleProperty().getValue());
        tgt.setVersionProperty(src.getVersionProperty().getValue());
    }
}