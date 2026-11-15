/**
 * Modelos exclusivos de documentación OpenAPI, sin intervención en el JSON de ejecución.
 *
 * <p>Convenciones para anotar los DTOs REST (contracts/README §3.1):</p>
 * <ul>
 *   <li>Los nombres de propiedades deben coincidir con JsonProperty / el JSON real (snake_case).</li>
 *   <li>Los identificadores del dominio se documentan como string con formato uuid, nunca como objetos.</li>
 *   <li>Money, BaseRate y BigDecimal monetario se documentan como string decimal, nunca como number.
 *       Las salidas habituales tienen 2 decimales; GET de parámetros financieros conserva 4.</li>
 *   <li>Percentage es string decimal de 0 a 100: su serializador produce 2 decimales;
 *       GET de parámetros financieros conserva 4. Los ejemplos de entrada deben respetar la
 *       precisión admitida por cada contrato, sin imponerle la escala de salida.</li>
 *   <li>Los arrays de identificadores contienen strings uuid. Sus límites y la aceptación de
 *       duplicados se documentan según el contrato y la implementación, no por suposición.</li>
 *   <li>Fechas: string/date. Instantes: string/date-time. Enums: valores reales permitidos.</li>
 *   <li>Obligatoriedad, nulabilidad y omisión son conceptos distintos: deben reflejar el JSON real.
 *       No se deducen solo de que un tipo Java sea primitivo o referencia.</li>
 *   <li>Los ejemplos y restricciones se contrastan con el contrato específico y los serializadores.
 *       No se añaden validaciones de ejecución para acomodar la documentación.</li>
 *   <li>Las respuestas de error referencian el schema ProblemDetails con application/problem+json,
 *       no con el tipo de contenido de la respuesta exitosa (JSON o CSV).</li>
 * </ul>
 * <p>Las anotaciones Swagger pertenecen a infraestructura; nunca al dominio ni a aplicación.</p>
 */
package com.seashare.seasharem3.infrastructure.adapter.in.web.dto.doc;
