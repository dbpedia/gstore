#!/bin/bash -i


########################
# HTTP CODE
########################

get_return_code() {
  CMD="curl -s -o /dev/null -w %{http_code} $1"
  >&2 printf "Test ${BLUE}%s${NORMAL}\n" "`echo -n $CMD | sed 's/&/\\\&/g'`"
  echo -n $($CMD)  
  #CMDDEBUG="curl -v $1"
  #>&2 printf "%s\n" "$($CMDDEBUG)"
}

post_return_code() {
  >&2 printf "Test ${BLUE}%s${NORMAL}\n" "curl -X POST -s -o /dev/null -w %{http_code}  \"$1\" "
  echo $(curl -X POST -s -o /dev/null -w %{http_code} $1 )
}

post_return_code_contenttype_applicationldjson() {
  >&2 printf "Test ${BLUE}%s${NORMAL}\n" "curl -X POST -s -o /dev/null -w %{http_code} -H \"Content-Type: application/ld+json\" -d $2 \"$1\" "
  echo $(curl -X POST -s -o /dev/null -w %{http_code} -H "Content-Type: application/ld+json" -d $2 $1 )
}

get_return_code_accept_applicationldjson() {
  >&2 printf "Test ${BLUE}%s${NORMAL}\n" "curl -s -o /dev/null -w %{http_code} -H \"Accept: application/ld+json\" \"$1\" "
  echo $(curl -s -o /dev/null -w %{http_code} -H "Accept: application/ld+json"  $1 )
}


########################
# BODY
########################

get_body() {
  >&2 printf "Test ${BLUE}%s${NORMAL}\n" "curl -s \"$1\" "
  echo $(curl -s  $1 )
}

get_body_accept_applicationldjson() {
  >&2 printf "Test ${BLUE}%s${NORMAL}\n" "curl -s  -H \"Accept: application/ld+json\" \"$1\" "
  echo $(curl -s  -H "Accept: application/ld+json"  $1 )
}

get_body_accept_textturle() {
  >&2 printf "Test ${BLUE}%s${NORMAL}\n" "curl -s  -H \"Accept: text/turtle\" \"$1\" "
  echo $(curl -s  -H "Accept: text/turtle"  $1 )
}

get_body_accept_textturle() {
  >&2 printf "Test ${BLUE}%s${NORMAL}\n" "curl -s  -H \"Accept: text/turtle\" \"$1\" "
  echo $(curl -s  -H "Accept: text/turtle"  $1 )
}


########################
# CONTENT-TYPE
########################

get_contenttype() {
  >&2 printf "Test ${BLUE}%s${NORMAL}\n" "curl -s  -o /dev/null -w %{content_type} \"$1\" "
  echo $(curl -s -o /dev/null -w %{content_type}  $1 )
}

get_contenttype_accept_applicationldjson() {
  >&2 printf "Test ${BLUE}%s${NORMAL}\n" "curl -s  -o /dev/null -w %{content_type} -H \"Accept: application/ld+json\" \"$1\" "
  echo $(curl -s -o /dev/null -w %{content_type} -H "Accept: application/ld+json"  $1 )
}

get_contenttype_accept_textturtle() {
  >&2 printf "Test ${BLUE}%s${NORMAL}\n" "curl -s  -o /dev/null -w %{content_type} -H \"Accept: text/turtle\" \"$1\" "
  echo $(curl -s -o /dev/null -w %{content_type} -H "Accept: text/turtle"  $1 )
}



check_valid_turtle() {
    rapper -i turtle -q -c  -O - - file <<<"$1"
	
	if [ $? -eq 0 ]; then
		echo "valid"
	else
		echo "invalid"
	fi
	#check="$?"
	#echo $check
}	

check_valid_json() {
	if jq -e . >/dev/null 2>&1 <<<"$1"; then
		echo "valid"
	else
		echo "invalid"
	fi
	
	}


rawurlencode() {
  local string="${1}"
  local strlen=${#string}
  local encoded=""
  local pos c o

  for (( pos=0 ; pos<strlen ; pos++ )); do
     c=${string:$pos:1}
     case "$c" in
        [-_.~a-zA-Z0-9] ) o="${c}" ;;
        * )               printf -v o '%%%02x' "'$c"
     esac
     encoded+="${o}"
  done
  echo "${encoded}"    # You can either set a return variable (FASTER) 
  REPLY="${encoded}"   #+or echo the result (EASIER)... or both... :p
}

#get_post_return_code() {
#  >&2 printf "Test ${BLUE}%s${NORMAL}\n" "curl -f -Li -H "Content-Type:application/json+ld" -d $2 $1 -o /dev/null -w '%{http_code}\n' -s"
#  echo $(curl -f -Li -H "Content-Type:application/json+ld" -d $2 $1 -o /dev/null -w '%{http_code}\n' -s)
#}

  # some debugging lines for get_return_doce
  #>&2 printf "Test ${BLUE}%s${NORMAL}\n" "curl -f -Li $1 -o /dev/null -w '%{http_code}\n' "
  #echo $(curl -f -Li $1 -o /dev/null -w '%{http_code}\n' -s)
  #RET=$CMD
  #>&2 printf "%s\n" "$RET"
  #RET="$($CMD)"
  #>&2 printf "%s\n" "$RET"
  
