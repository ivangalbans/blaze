#!/usr/bin/env bash

# Usage: ./evaluate-measure-subject-list.sh -f <query>.cql <expected-count>

# Takes a CQL file, creates a Library resource from it, references that from a
# Measure resource and calls $evaluate-measure with reportType subject-list on it.

library() {
cat <<END
{
  "resourceType": "Library",
  "status": "active",
  "type" : {
    "coding" : [
      {
        "system": "http://terminology.hl7.org/CodeSystem/library-type",
        "code" : "logic-library"
      }
    ]
  },
  "content": [
    {
      "contentType": "text/cql"
    }
  ]
}
END
}

measure() {
cat <<END
{
  "resourceType": "Measure",
  "status": "active",
  "scoring": {
    "coding": [
      {
        "system": "http://terminology.hl7.org/CodeSystem/measure-scoring",
        "code": "cohort"
      }
    ]
  },
  "group": [
    {
      "population": [
        {
          "code": {
            "coding": [
              {
                "system": "http://terminology.hl7.org/CodeSystem/measure-population",
                "code": "initial-population"
              }
            ]
          },
          "criteria": {
            "language": "text/cql-identifier",
            "expression": "InInitialPopulation"
          }
        }
      ]
    }
  ]
}
END
}

parameters() {
cat <<END
{
  "resourceType": "Parameters",
  "parameter": [
    {
      "name": "periodStart",
      "valueDate": "2000"
    },
    {
      "name": "periodEnd",
      "valueDate": "2030"
    },
    {
      "name": "reportType",
      "valueCode": "subject-list"
    }
  ]
}
END
}

create-library() {
  library | jq -cM ".url = \"urn:uuid:$1\" | .content[0].data = \"$2\""
}

create-measure() {
  measure | jq -cM ".url = \"urn:uuid:$1\" | .library[0] = \"urn:uuid:$2\""
}

post() {
  curl -sH "Content-Type: application/fhir+json" -d @- "$1/$2"
}

evaluate-measure() {
  parameters | curl -sH "Content-Type: application/fhir+json" -d @- "$1/Measure/$2/\$evaluate-measure"
}

fetch-patients() {
  curl -s "$1/Patient?_list=$2&_count=200"
}

BASE="http://localhost:8080/fhir"
NAME=$1
EXPECTED_COUNT=$2

DATA=$(base64 "modules/operation-measure-evaluate-measure/test/blaze/fhir/operation/evaluate_measure/$NAME.cql" | tr -d '\n')
LIBRARY_URI=$(uuidgen | tr '[:upper:]' '[:lower:]')
MEASURE_URI=$(uuidgen | tr '[:upper:]' '[:lower:]')

create-library "$LIBRARY_URI" "$DATA" | post "$BASE" "Library" > /dev/null

MEASURE_ID=$(create-measure "$MEASURE_URI" "$LIBRARY_URI" | post "$BASE" "Measure" | jq -r .id)
REPORT=$(evaluate-measure "$BASE" "$MEASURE_ID")
COUNT=$(echo "$REPORT" | jq -r ".group[0].population[0].count")

if [ "$COUNT" = "$EXPECTED_COUNT" ]; then
  echo "Success: count ($COUNT) equals the expected count"
else
  echo "Fail: count ($COUNT) != $EXPECTED_COUNT"
  echo "Report:"
  echo "$REPORT" | jq .
  exit 1
fi

LIST_ID=$(echo "$REPORT" | jq -r '.group[0].population[0].subjectResults.reference | split("/")[1]')
PATIENT_BUNDLE=$(fetch-patients "$BASE" "$LIST_ID")
ID_COUNT=$(echo "$PATIENT_BUNDLE" | jq -r ".entry[].resource.id" | sort -u | wc -l | xargs)

if [ "$ID_COUNT" = "$EXPECTED_COUNT" ]; then
  echo "Success: downloaded patient count ($ID_COUNT) equals the expected count"
else
  echo "Fail: downloaded patient count ($ID_COUNT) != $EXPECTED_COUNT"
  echo "Patient bundle:"
  echo "$PATIENT_BUNDLE" | jq .
  exit 1
fi
