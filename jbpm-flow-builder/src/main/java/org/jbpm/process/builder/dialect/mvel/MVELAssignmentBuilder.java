package org.jbpm.process.builder.dialect.mvel;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.drools.base.mvel.MVELCompilationUnit;
import org.drools.compiler.BoundIdentifiers;
import org.drools.compiler.DescrBuildError;
import org.drools.compiler.ReturnValueDescr;
import org.drools.rule.MVELDialectRuntimeData;
import org.drools.rule.builder.PackageBuildContext;
import org.drools.rule.builder.dialect.mvel.MVELAnalysisResult;
import org.drools.rule.builder.dialect.mvel.MVELDialect;
import org.jbpm.process.builder.AssignmentBuilder;
import org.jbpm.process.core.ContextResolver;
import org.jbpm.process.core.context.variable.VariableScope;
import org.jbpm.process.instance.impl.MVELAssignmentAction;
import org.jbpm.workflow.core.node.Assignment;

public class MVELAssignmentBuilder implements AssignmentBuilder {

	public void build(PackageBuildContext context, Assignment assignment, String sourceExpr, String targetExpr,
			ContextResolver contextResolver, boolean isInput) {

		try {
			MVELDialect dialect = (MVELDialect) context.getDialect("mvel");
			
            boolean typeSafe = context.isTypesafe();

            Map<String, Class<?>> variables = new HashMap<String,Class<?>>();
            
            context.setTypesafe( false ); // we can't know all the types ahead of time with processes, but we don't need return types, so it's ok
            BoundIdentifiers boundIdentifiers = new BoundIdentifiers(variables, context.getPackageBuilder().getGlobals());
            MVELAnalysisResult analysis = ( MVELAnalysisResult ) dialect.analyzeBlock( context,
                    null,
                    dialect.getInterceptors(),
                    assignment.getFrom(),
                    boundIdentifiers,
                    null,
                    "context",
                    org.drools.runtime.process.ProcessContext.class );                      
            context.setTypesafe( typeSafe );
            
            Set<String> variableNames = analysis.getNotBoundedIdentifiers();
            
            if (contextResolver != null) {
                for (String variableName: variableNames) {
                    if (  analysis.getMvelVariables().keySet().contains( variableName ) ||  variableName.equals( "kcontext" ) || variableName.equals( "context" ) ) {
                        continue;
                    }                    
                    VariableScope variableScope = (VariableScope) contextResolver.resolveContext(VariableScope.VARIABLE_SCOPE, variableName);
                    if (variableScope == null) {
                        context.getErrors().add(
                            new DescrBuildError(
                                context.getParentDescr(),
                                new ReturnValueDescr(assignment.getFrom()),
                                null,
                                "Could not find variable '" + variableName + "' for expression '" + assignment.getFrom() + "'" ) );                    
                    } else {
                        variables.put(variableName,
                                      context.getDialect().getTypeResolver().resolveType(variableScope.findVariable(variableName).getType().getStringType()));
                    }
                }
            }
            
            MVELCompilationUnit unit = dialect.getMVELCompilationUnit( assignment.getFrom(),
                    analysis,
                    null,
                    null,
                    variables,
                    context,
                    "context",
                    org.drools.runtime.process.ProcessContext.class);              
            MVELAssignmentAction expr = new MVELAssignmentAction(unit, context.getDialect().getId(), assignment, sourceExpr, targetExpr, isInput );
            
            assignment.setMetaData("Action",  expr );
            
            MVELDialectRuntimeData data = (MVELDialectRuntimeData) context.getPkg().getDialectRuntimeRegistry().getDialectData( dialect.getId() );
            // I dont know if this is correct
            data.addCompileable( expr );  
            
            expr.compile( data );

		} catch (final Exception e) {
			context.getErrors().add(
					new DescrBuildError(context.getParentDescr(),  new ReturnValueDescr(assignment.getFrom()), null, "Unable to build expression for 'assignement' "
							+ assignment.getFrom() + "': " + e));
		}

	}

}
