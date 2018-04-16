#!/bin/bash


printf "\n"

dropDb() {
    #############################################################
    ## DELETE EXISTING DATABASE
    #############################################################
    
    printf "#############################################################\n"
    printf "## Removing existing lucene databases\n"
    printf "#############################################################\n"
    find . -name luceneStore -type d -print0 | xargs -0 rm -rf --
    printf "\n"
    
    ## Delete logs
    printf "#############################################################\n"
    printf "## Removing existing log directories\n"
    printf "#############################################################\n"
    find . -name logs -type d -print0 | xargs -0 rm -rf --
    printf "\n"
}


cleanClientBuild() {
    #############################################################
    ## CLEAN the UI build files
    #############################################################
    printf "#############################################################\n"
    printf "## Removing existing UI bundle\n"
    printf "#############################################################\n"
    rm -rf client/build
    printf "\n"
}


deleteNodeModules() {
    #############################################################
    ## DELETE existing node_modules directories
    #############################################################
    printf "#############################################################\n"
    printf "## Removing existing node_modules directories\n"
    printf "#############################################################\n"
    find . -name node_modules -type d -print0 | xargs -0 rm -rf --
    npm cache clean --force
    npm cache verify
    printf "\n"
}


rebuildNodeModules() {
    #############################################################
    ## REBUILD node modules
    ## We have our own version of the webpack that we copy to the
    ## node_modules/react-scripts director for bundle creation
    #############################################################
    printf "#############################################################\n"
    printf "## Rebuilding node modules\n"
    printf "#############################################################\n"
    cd client
    npm install
    cp -r buildconfig/ node_modules/react-scripts/
    ## make testcafe global
    npm install testcafe -g
    cd ..
    npm install
    printf "\n"
}


buildMozartLink() {
    #############################################################
    ## CREATE the mozart node module symlink
    #############################################################
    printf "#############################################################\n"
    printf "## Creating mozart module symlink\n"
    printf "#############################################################\n"
    npm link
    printf "\n"
}


#############################################################
## PRINT environment variables (meaning any we've added should show up)
#############################################################
printf "ENVIRONMENT VARIABLES...\n"
printf "#############################################################\n"
printenv
printf "#############################################################\n"

## DONE
printf "\n\n"


echo "updating config"
deleteNodeModules
rebuildNodeModules
buildMozartLink


