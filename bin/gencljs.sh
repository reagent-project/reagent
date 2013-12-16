#! /bin/bash

src=$1
ns=$2

function skipkeywords () {
    local kw=(break case catch continue debugger default delete do else
        finally for function if in instanceof new return switch
        this throw try typeof var void while with undefined null
        class enum export extends import super
        implements interface let package private protected public static
        yield true false long char boolean string apply call prototype
        constructor contains concat bind base array drop get list count isArray
        map key max min meta create name object repeat set type core trim now
        some sort splice split slice remove pop offset log js filter extend
        reverse str join keys length test first replace cons
        charAt charCodeAt)
    local kws=${kw[*]}
    local kwsplit=${kws// /\\|}
    local keywords="^\\($kwsplit\\)$"
    grep -v "$keywords"
}

function propnames() {
    (# grep -o '\.[a-zA-Z$][a-zA-Z0-9_$]*' $1;
        grep -o '[a-zA-Z$][a-zA-Z0-9$_]*:' $1) |
    sed 's,[:.],,g' | sort | skipkeywords | uniq
}

function genexterns() {
    echo "(function() {var X = function(){};"
    cat "$1" | propnames |
    # sed 's,\(.*\),/** @expose */\\nX.\1 = function () {};,' 
    sed 's,\(.*\),/** @expose */\\nX.\1 = true;,' 
    echo "})();"
}

function quote() {
    sed -e 's,\\,\\\\,g' -e 's,",\\",g'
}

function skipdockeywords () {
    sed "s,^[ 	]*[*] *@[a-zA-Z].*,,"
}

function printns() {
    echo "(ns $ns)"
}

function printjs() {
    echo -n "(js* \""
    cat "$1" | quote | skipdockeywords
    genexterns "$1" "$ns"
    echo "$ns.React = (typeof(window) != 'undefined' ? window.React : global.React);"
    # echo "if (typeof(window) != 'undefined') window['React'] = $ns.React; else global['React'] = $ns.React;"
    echo "\")"
}

printns
printjs "$src"

# Could also do something like 
