/**
 *
 * Copyright (C) 2006-2011  Syed Asad Rahman <asad@ebi.ac.uk>
 *
 * Contact: cdk-devel@lists.sourceforge.net
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2.1
 * of the License, or (at your option) any later version.
 * All we ask is that proper credit is given for our work, which includes
 * - but is not limited to - adding the above copyright notice to the beginning
 * of your source code files, and to any copyright notice that you may distribute
 * with programs based on this work.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 */
package org.openscience.smsd.tools;

import org.openscience.cdk.CDKConstants;
import org.openscience.cdk.DefaultChemObjectBuilder;
import org.openscience.cdk.annotations.TestClass;
import org.openscience.cdk.annotations.TestMethod;
import org.openscience.cdk.aromaticity.CDKHueckelAromaticityDetector;
import org.openscience.cdk.graph.ConnectivityChecker;
import org.openscience.cdk.interfaces.IAtom;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.interfaces.IMolecule;
import org.openscience.cdk.interfaces.IMoleculeSet;
import org.openscience.cdk.interfaces.IRing;
import org.openscience.cdk.interfaces.IRingSet;
import org.openscience.cdk.ringsearch.AllRingsFinder;
import org.openscience.cdk.tools.CDKHydrogenAdder;
import org.openscience.cdk.tools.ILoggingTool;
import org.openscience.cdk.tools.LoggingToolFactory;
import org.openscience.cdk.tools.manipulator.AtomContainerManipulator;
import org.openscience.cdk.tools.manipulator.RingSetManipulator;

/**
 * Class that cleans a molecule before MCS search.
 * @cdk.module smsd
 * @cdk.githash
 * @author Syed Asad Rahman <asad@ebi.ac.uk>
 */
@TestClass("org.openscience.cdk.smsd.tools.MoleculeSanityCheckTest")
public class MoleculeSanityCheck {

    private static final ILoggingTool logger =
            LoggingToolFactory.createLoggingTool(MoleculeSanityCheck.class);

    /**
     * Modules for cleaning a molecule
     * @param molecule
     * @return cleaned GraphAtomContainer
     */
    @TestMethod("testCheckAndCleanMolecule")
    public static IAtomContainer checkAndCleanMolecule(IAtomContainer molecule) {
        boolean isMarkush = false;
        for (IAtom atom : molecule.atoms()) {
            if (atom.getSymbol().equals("R")) {
                isMarkush = true;
                break;
            }
        }

        if (isMarkush) {
            logger.warn("Skipping Markush structure for sanity check");
        }

        // Check for salts and such
        if (!ConnectivityChecker.isConnected(molecule)) {
            // lets see if we have just two parts if so, we assume its a salt and just work
            // on the larger part. Ideally we should have a check to ensure that the smaller
            //  part is a metal/halogen etc.
            IMoleculeSet fragments = ConnectivityChecker.partitionIntoMolecules(molecule);
            if (fragments.getMoleculeCount() > 2) {
                logger.warn("More than 2 components. Skipped");
            } else {
                IMolecule frag1 = fragments.getMolecule(0);
                IMolecule frag2 = fragments.getMolecule(1);
                if (frag1.getAtomCount() > frag2.getAtomCount()) {
                    molecule = frag1;
                } else {
                    molecule = frag2;
                }
            }
        }
        aromatizeMolecule(molecule);
        return molecule;
    }

    /**
     * This function finds rings and uses aromaticity detection code to
     * aromatize the molecule.
     * @param mol input molecule
     */
    @TestMethod("testAromatizeMolecule")
    public static void aromatizeMolecule(IAtomContainer mol) {
        // need to find rings and aromaticity again since added H's

        IRingSet ringSet = null;
        try {
            AllRingsFinder arf = new AllRingsFinder();
            ringSet = arf.findAllRings(mol);
        } catch (Exception exception) {
            logger.warn("ERROR: ", exception.getMessage());
            logger.debug(exception);
        }

        // figure out which atoms are in aromatic rings:
        try {
            CDKHydrogenAdder cdk = CDKHydrogenAdder.getInstance(DefaultChemObjectBuilder.getInstance());
            cdk.addImplicitHydrogens(mol);
        } catch (Exception exception) {
        }

        try {
            // figure out which atoms are in aromatic rings:
//            printAtoms(atomContainer);
            AtomContainerManipulator.percieveAtomTypesAndConfigureAtoms(mol);
//            printAtoms(atomContainer);
            CDKHueckelAromaticityDetector.detectAromaticity(mol);
//            printAtoms(atomContainer);
            // figure out which rings are aromatic:
            RingSetManipulator.markAromaticRings(ringSet);
//            printAtoms(atomContainer);
            // figure out which simple (non cycles) rings are aromatic:
            // HueckelAromaticityDetector.detectAromaticity(atomContainer, srs);
        } catch (Exception e) {
            e.printStackTrace();
        }


        // only atoms in 6 membered rings are aromatic
        // determine largest ring that each atom is atom part of

        for (int i = 0; i <= mol.getAtomCount() - 1; i++) {

            mol.getAtom(i).setFlag(CDKConstants.ISAROMATIC, false);

            jloop:
            for (int j = 0; j <= ringSet.getAtomContainerCount() - 1; j++) {
                //logger.debug(i+"\t"+j);
                IRing ring = (IRing) ringSet.getAtomContainer(j);
                if (!ring.getFlag(CDKConstants.ISAROMATIC)) {
                    continue jloop;
                }

                boolean haveatom = ring.contains(mol.getAtom(i));

                //logger.debug("haveatom="+haveatom);

                if (haveatom && ring.getAtomCount() == 6) {
                    mol.getAtom(i).setFlag(CDKConstants.ISAROMATIC, true);
                }
            }
        }
    }
}
