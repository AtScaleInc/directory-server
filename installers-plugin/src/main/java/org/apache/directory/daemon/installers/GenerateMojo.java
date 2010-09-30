/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *  
 *    http://www.apache.org/licenses/LICENSE-2.0
 *  
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License. 
 *  
 */
package org.apache.directory.daemon.installers;


import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.apache.directory.daemon.installers.archive.ArchiveInstallerCommand;
import org.apache.directory.daemon.installers.archive.ArchiveTarget;
import org.apache.directory.daemon.installers.bin.BinInstallerCommand;
import org.apache.directory.daemon.installers.bin.BinTarget;
import org.apache.directory.daemon.installers.deb.DebInstallerCommand;
import org.apache.directory.daemon.installers.deb.DebTarget;
import org.apache.directory.daemon.installers.macosxpkg.MacOsXPkgInstallerCommand;
import org.apache.directory.daemon.installers.macosxpkg.MacOsXPkgTarget;
import org.apache.directory.daemon.installers.nsis.NsisInstallerCommand;
import org.apache.directory.daemon.installers.nsis.NsisTarget;
import org.apache.directory.daemon.installers.rpm.RpmInstallerCommand;
import org.apache.directory.daemon.installers.rpm.RpmTarget;
import org.apache.directory.daemon.installers.solarispkg.SolarisPkgInstallerCommand;
import org.apache.directory.daemon.installers.solarispkg.SolarisPkgTarget;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.FileUtils;


/**
 * Maven 2 mojo creating the platform specific installation layout images.
 * 
 * @goal generate
 * @description Creates platform specific installation layout images.
 * @phase package
 * @requiresDependencyResolution runtime
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 */
public class GenerateMojo extends AbstractMojo
{
    /**
     * The target directory into which the mojo creates os and platform 
     * specific images.
     * 
     * @parameter default-value="${project.build.directory}/installers"
     */
    private File outputDirectory;

    /**
     * The associated maven project.
     * 
     * @parameter expression="${project}" default-value="${project}"
     * @required
     */
    private MavenProject project;

    /**
     * The RPM installer targets.
     * 
     * @parameter
     */
    private RpmTarget[] rpmTargets;

    /**
     * The Mac OS X installer targets.
     * 
     * @parameter
     */
    private MacOsXPkgTarget[] macOsXPkgTargets;

    /**
     * The Solaris PKG installers targets.
     * 
     * @parameter
     */
    private SolarisPkgTarget[] solarisPkgTargets;

    /**
     * The NSIS installer targets.
     * 
     * @parameter
     */
    private NsisTarget[] nsisTargets;

    /**
     * The Debian installer targets.
     * 
     * @parameter
     */
    private DebTarget[] debTargets;

    /**
     * The Binary installer targets.
     * 
     * @parameter
     */
    private BinTarget[] binTargets;

    /**
     * The Archive installers targets.
     * 
     * @parameter
     */
    private ArchiveTarget[] archiveTargets;

    /**
     * The exclusions.
     * 
     * @parameter
     */
    private Set excludes;

    /** The list containing all the targets */
    private List<Target> allTargets = new ArrayList<Target>();


    /**
     * {@inheritDoc}
     */
    public void execute() throws MojoExecutionException, MojoFailureException
    {
        // Collecting all targets 
        collectAllTargets();

        // Returning if there is no target to build 
        if ( allTargets.isEmpty() )
        {
            getLog().info( "No installers to generate." );
            return;
        }

        getLog().info( "-------------------------------------------------------" );

        // Creating the output directory
        FileUtils.mkdir( outputDirectory.getAbsolutePath() );

        // Generating installers for all targets
        for ( Target target : allTargets )
        {
            getLog().info( "Executing target '" + target.getId() + "'" );
            getLog().info( "--------------------" );
            getLog().info( "OS Name: " + target.getOsName() );
            getLog().info( "OS Arch: " + target.getOsArch() );
            getLog().info( "--------------------" );

            // Archive target
            if ( target instanceof ArchiveTarget )
            {
                ArchiveInstallerCommand archiveCmd = new ArchiveInstallerCommand( this, ( ArchiveTarget ) target );
                archiveCmd.execute();
            }

            // Bin target
            if ( target instanceof BinTarget )
            {
                BinInstallerCommand binCmd = new BinInstallerCommand( this, ( BinTarget ) target );
                binCmd.execute();
            }

            // Deb target
            if ( target instanceof DebTarget )
            {
                DebInstallerCommand debCmd = new DebInstallerCommand( this, ( DebTarget ) target );
                debCmd.execute();
            }

            // Mac OS X PKG target
            if ( target instanceof MacOsXPkgTarget )
            {
                MacOsXPkgInstallerCommand pkgCmd = new MacOsXPkgInstallerCommand( this, ( MacOsXPkgTarget ) target );
                pkgCmd.execute();
            }

            // NSIS target
            if ( target instanceof NsisTarget )
            {
                NsisInstallerCommand nsisCmd = new NsisInstallerCommand( this, ( NsisTarget ) target );
                nsisCmd.execute();
            }

            // RPM target
            if ( target instanceof RpmTarget )
            {
                RpmInstallerCommand rpmCmd = new RpmInstallerCommand( this, ( RpmTarget ) target );
                rpmCmd.execute();
            }

            // Solaris PKG target
            if ( target instanceof SolarisPkgTarget )
            {
                SolarisPkgInstallerCommand pkgCmd = new SolarisPkgInstallerCommand( this, ( SolarisPkgTarget ) target );
                pkgCmd.execute();
            }

            getLog().info( "-------------------------------------------------------" );
        }
    }


    /**
     * Collects all targets.
     */
    private void collectAllTargets()
    {
        addAllTargets( allTargets, nsisTargets );
        addAllTargets( allTargets, rpmTargets );
        addAllTargets( allTargets, debTargets );
        addAllTargets( allTargets, macOsXPkgTargets );
        addAllTargets( allTargets, solarisPkgTargets );
        addAllTargets( allTargets, binTargets );
        addAllTargets( allTargets, archiveTargets );
    }


    /**
     * Adds an array of targets to the given list.
     *
     * @param list
     *      the list of targets
     * @param array
     *      an array of targets
     */
    private void addAllTargets( List<Target> list, Target[] array )
    {
        if ( ( list != null ) && ( array != null ) )
        {
            list.addAll( Arrays.asList( array ) );
        }
    }


    /**
     * Gets the output directory.
     *
     * @return
     *      the output directory
     */
    public File getOutputDirectory()
    {
        return outputDirectory;
    }


    /**
     * Gets the associated Maven project.
     *
     * @return
     *      the associated Maven project
     */
    public MavenProject getProject()
    {
        return project;
    }


    public Set getExcludes()
    {
        return excludes;
    }
}
